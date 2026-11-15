package com.example.ytglass

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

class DownloadResult(val title: String, val quality: String)

private class Fmt(val url: String, val mime: String, val height: Int, val bitrate: Int, val size: Long)

object Downloader {
    private const val UA =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val CHUNK = 10L * 1024 * 1024
    private val ID = Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})")

    suspend fun run(
        ctx: Context,
        link: String,
        onProgress: (String, Float, String?) -> Unit
    ): DownloadResult {
        val id = ID.find(link)?.groupValues?.get(1) ?: error("Ese link no parece de YouTube")
        onProgress("Buscando el video…", 0f, null)

        val json = fetchPlayer(id)
        val status = json.optJSONObject("playabilityStatus")
        if (status?.optString("status") != "OK") {
            error(status?.optString("reason")?.ifEmpty { null } ?: "No se puede descargar este video")
        }
        val title = json.optJSONObject("videoDetails")?.optString("title") ?: "video"
        val streaming = json.optJSONObject("streamingData") ?: error("YouTube no devolvió streams")

        val adaptive = parse(streaming.optJSONArray("adaptiveFormats"))
        val video = adaptive
            .filter { it.mime.startsWith("video/mp4") && it.mime.contains("avc1") }
            .maxByOrNull { it.height * 1_000_000L + it.bitrate }
        val audio = adaptive
            .filter { it.mime.startsWith("audio/mp4") }
            .maxByOrNull { it.bitrate }

        val tmp = File(ctx.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
        val out = File(tmp, "out.mp4")
        try {
            val quality: String
            if (video != null && audio != null) {
                val total = (video.size + audio.size).toFloat()
                var done = 0L
                var last = -1f
                val report = { n: Long ->
                    done += n
                    val p = done / total * 0.93f
                    if (p - last >= 0.004f) {
                        last = p
                        onProgress("Descargando ${video.height}p…", p, title)
                    }
                }
                val vf = File(tmp, "v.mp4")
                val af = File(tmp, "a.m4a")
                download(video, vf, report)
                download(audio, af, report)
                onProgress("Uniendo video y audio…", 0.95f, title)
                mux(vf, af, out)
                quality = "${video.height}p"
            } else {
                val prog = parse(streaming.optJSONArray("formats"))
                    .filter { it.mime.startsWith("video/mp4") }
                    .maxByOrNull { it.height } ?: error("No hay formatos MP4 disponibles")
                var done = 0L
                var last = -1f
                download(prog, out) { n ->
                    done += n
                    val p = done / prog.size.toFloat() * 0.95f
                    if (p - last >= 0.004f) {
                        last = p
                        onProgress("Descargando ${prog.height}p…", p, title)
                    }
                }
                quality = "${prog.height}p"
            }
            onProgress("Guardando en Películas…", 0.98f, title)
            save(ctx, out, title)
            return DownloadResult(title, quality)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun fetchPlayer(id: String): JSONObject {
        val client = JSONObject()
            .put("clientName", "ANDROID_VR")
            .put("clientVersion", "1.60.19")
            .put("deviceMake", "Oculus")
            .put("deviceModel", "Quest 3")
            .put("androidSdkVersion", 32)
            .put("osName", "Android")
            .put("osVersion", "12L")
            .put("hl", "es")
            .put("gl", "US")
        val body = JSONObject()
            .put("videoId", id)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", client))

        val c = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("X-YouTube-Client-Name", "28")
            c.setRequestProperty("X-YouTube-Client-Version", "1.60.19")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) error("YouTube respondió ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun parse(arr: JSONArray?): List<Fmt> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url")
            val size = o.optString("contentLength").toLongOrNull() ?: 0L
            if (url.isEmpty() || size <= 0) null
            else Fmt(url, o.optString("mimeType"), o.optInt("height"), o.optInt("bitrate"), size)
        }
    }

    private suspend fun download(f: Fmt, dest: File, onBytes: (Long) -> Unit) {
        dest.outputStream().buffered().use { out ->
            var pos = 0L
            val buf = ByteArray(64 * 1024)
            while (pos < f.size) {
                currentCoroutineContext().ensureActive()
                val end = minOf(pos + CHUNK - 1, f.size - 1)
                val c = URL(f.url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    c.setRequestProperty("User-Agent", UA)
                    c.setRequestProperty("Range", "bytes=$pos-$end")
                    if (c.responseCode !in 200..299) error("Error de descarga (HTTP ${c.responseCode})")
                    c.inputStream.use { ins ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                            onBytes(n.toLong())
                        }
                    }
                } finally {
                    c.disconnect()
                }
            }
        }
    }

    private fun mux(video: File, audio: File, out: File) {
        val ve = MediaExtractor()
        val ae = MediaExtractor()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            ve.setDataSource(video.path)
            ae.setDataSource(audio.path)
            val vi = trackOf(ve, "video/")
            val ai = trackOf(ae, "audio/")
            ve.selectTrack(vi)
            ae.selectTrack(ai)
            val vt = muxer.addTrack(ve.getTrackFormat(vi))
            val at = muxer.addTrack(ae.getTrackFormat(ai))
            muxer.start()
            copy(ve, muxer, vt)
            copy(ae, muxer, at)
            muxer.stop()
        } finally {
            ve.release()
            ae.release()
            muxer.release()
        }
    }

    private fun trackOf(e: MediaExtractor, prefix: String): Int {
        for (i in 0 until e.trackCount) {
            if (e.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        }
        error("Falta la pista de $prefix")
    }

    private fun copy(e: MediaExtractor, m: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val n = e.readSampleData(buf, 0)
            if (n < 0) break
            val key = (e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            info.set(0, n, e.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            m.writeSampleData(track, buf, info)
            e.advance()
        }
    }

    private fun save(ctx: Context, file: File, title: String) {
        val name = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifEmpty { "video" } + ".mp4"
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YTGlass")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: error("No se pudo crear el archivo")
        r.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        v.clear()
        v.put(MediaStore.Video.Media.IS_PENDING, 0)
        r.update(uri, v, null, null)
    }
}
