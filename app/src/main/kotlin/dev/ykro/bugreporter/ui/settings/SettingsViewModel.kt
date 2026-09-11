package dev.ykro.bugreporter.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ykro.bugreporter.agent.ModelStore
import dev.ykro.bugreporter.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
  val privateMode: Boolean = false,
  val modelPresent: Boolean = false,
  val modelFileName: String? = null,
  val downloading: Boolean = false,
  val progress: Float = 0f,
  val error: String? = null,
  val pushCommand: String = "",
  val gitHubConfigured: Boolean = false,
  val gitHubRepo: String = "",
)

class SettingsViewModel(private val context: Context, private val settings: SettingsStore, gitHubConfigured: Boolean, gitHubRepo: String) : ViewModel() {
  private val _state = MutableStateFlow(SettingsUiState(gitHubConfigured = gitHubConfigured, gitHubRepo = gitHubRepo, pushCommand = ModelStore.pushCommand(context)))
  val state: StateFlow<SettingsUiState> = _state
  private var downloadJob: Job? = null

  init {
    viewModelScope.launch { settings.privateModeEnabled.collect { on -> _state.update { it.copy(privateMode = on) } } }
    refreshModel()
  }

  fun refreshModel() {
    val file = ModelStore.find(context)
    _state.update { it.copy(modelPresent = file != null, modelFileName = file?.name) }
  }

  fun setPrivateMode(on: Boolean) = viewModelScope.launch { settings.setPrivateMode(on) }

  fun download() {
    if (downloadJob?.isActive == true) return
    downloadJob =
      viewModelScope.launch {
        _state.update { it.copy(downloading = true, progress = 0f, error = null) }
        try {
          ModelStore.download(context).collect { p -> _state.update { it.copy(progress = p) } }
        } catch (e: Exception) {
          Timber.e(e, "Model download failed")
          _state.update { it.copy(error = e.message ?: "Download failed") }
        }
        _state.update { it.copy(downloading = false) }
        refreshModel()
      }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
    _state.update { it.copy(downloading = false) }
  }

  fun deleteModel() {
    ModelStore.delete(context)
    refreshModel()
  }
}
