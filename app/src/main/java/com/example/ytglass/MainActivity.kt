package com.example.ytglass

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data class Working(val stage: String, val progress: Float, val title: String?) : UiState
    data class Done(val title: String, val quality: String) : UiState
    data class Error(val message: String) : UiState
}

class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(link: String) {
        if (job?.isActive == true) return
        _state.value = UiState.Working("Buscando el video…", 0f, null)
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = Downloader.run(getApplication(), link.trim()) { stage, p, title ->
                    _state.value = UiState.Working(stage, p, title)
                }
                _state.value = UiState.Done(r.title, r.quality)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun reset() {
        job?.cancel()
        _state.value = UiState.Idle
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}
