package com.example.ytglass

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Accent = Color(0xFF7C6CFF).copy(alpha = 0.55f)

@Composable
fun App(vm: DownloadViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060F))) {
        Aurora()
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 4 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YT Glass", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("Videos en la mejor calidad", color = Color.White.copy(0.65f), fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(900, delayMillis = 200)) +
                    slideInVertically(tween(900, delayMillis = 200)) { it / 3 }
            ) {
                Glass(Modifier.fillMaxWidth().animateContentSize()) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.92f)) togetherWith
                                fadeOut(tween(180))
                        },
                        contentKey = { it::class },
                        label = "card"
                    ) { s ->
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (s) {
                                is UiState.Idle -> {
                                    GlassField(link) { link = it }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        GlassButton("Pegar", Modifier.weight(1f)) {
                                            clipboard.getText()?.text?.let { link = it }
                                        }
                                        GlassButton(
                                            "Descargar", Modifier.weight(1.6f),
                                            enabled = link.isNotBlank(), tint = Accent
                                        ) { vm.start(link) }
                                    }
                                }
                                is UiState.Working -> {
                                    Text(s.stage, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    s.title?.let {
                                        Text(
                                            it, color = Color.White.copy(0.6f), fontSize = 13.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                                        )
                                    }
                                    GlassProgress(s.progress)
                                    Text("${(s.progress * 100).toInt()}%", color = Color.White.copy(0.8f), fontSize = 14.sp)
                                    GlassButton("Cancelar", Modifier.fillMaxWidth()) { vm.reset() }
                                }
                                is UiState.Done -> {
                                    Check()
                                    Text("¡Listo!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${s.title}\nGuardado en Películas/YTGlass · ${s.quality}",
                                        color = Color.White.copy(0.7f), fontSize = 14.sp, textAlign = TextAlign.Center
                                    )
                                    GlassButton("Otro video", Modifier.fillMaxWidth(), tint = Accent) {
                                        link = ""
                                        vm.reset()
                                    }
                                }
                                is UiState.Error -> {
                                    Text("Algo salió mal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(s.message, color = Color.White.copy(0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                                    GlassButton("Reintentar", Modifier.fillMaxWidth(), tint = Accent) { vm.reset() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Aurora() {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        1f, 0f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        fun blob(c: Color, cx: Float, cy: Float, r: Float) {
            val center = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(c.copy(0.55f), Color.Transparent), center, r), r, center)
        }
        val m = size.minDimension
        blob(Color(0xFF6C5CE7), size.width * (0.15f + 0.6f * a), size.height * (0.15f + 0.15f * b), m * 0.85f)
        blob(Color(0xFF00B4D8), size.width * (0.85f - 0.6f * a), size.height * (0.55f + 0.2f * b), m * 0.8f)
        blob(Color(0xFFFF6FB5), size.width * (0.3f + 0.4f * b), size.height * (0.9f - 0.2f * a), m * 0.7f)
    }
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.05f))))
            .background(tint)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.65f), Color.White.copy(0.04f), Color.White.copy(0.28f))),
                shape
            ),
        content = content
    )
}

@Composable
fun GlassField(value: String, onChange: (String) -> Unit) {
    Glass(Modifier.fillMaxWidth(), radius = 18.dp) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Pegá el link de YouTube", color = Color.White.copy(0.45f), fontSize = 16.sp)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Transparent,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    Glass(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        radius = 20.dp,
        tint = tint
    ) {
        Text(
            text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center).padding(vertical = 16.dp, horizontal = 12.dp)
        )
    }
}

@Composable
fun GlassProgress(progress: Float) {
    val p by animateFloatAsState(progress, tween(400), label = "progress")
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sh"
    )
    Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Color.White.copy(0.12f))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p.coerceIn(0.03f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF7C6CFF), Color(0xFF00D0FF))))
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(0.55f), Color.Transparent),
                            startX = w * shimmer - 120f,
                            endX = w * shimmer + 120f
                        )
                    )
                }
        )
    }
}

@Composable
fun Check() {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
    Canvas(Modifier.size(72.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }) {
        drawCircle(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF06B6D4))))
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.35f)
        }
        drawPath(
            path, Color.White,
            style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
