package net.xiondigital.anlasoz.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.ai.AiException
import net.xiondigital.anlasoz.ai.AiFailure
import net.xiondigital.anlasoz.ai.ProviderPreset
import net.xiondigital.anlasoz.ai.ProviderPresets
import net.xiondigital.anlasoz.ai.detail
import net.xiondigital.anlasoz.ai.headline
import net.xiondigital.anlasoz.audio.TtsState
import net.xiondigital.anlasoz.ai.LocalModelState
import net.xiondigital.anlasoz.data.model.ReplyStyle
import net.xiondigital.anlasoz.data.repo.LibraryStats
import net.xiondigital.anlasoz.local.DownloadState
import net.xiondigital.anlasoz.local.LocalModelFile
import net.xiondigital.anlasoz.local.LocalModelSource
import net.xiondigital.anlasoz.local.ModelCatalog

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data class Success(val detail: String) : ConnectionTest
    data class Failed(val headline: String, val detail: String) : ConnectionTest
}

data class SettingsUiState(
    val presetId: String = ProviderPresets.freebuff.id,
    val baseUrl: String = "",
    val model: String = "",
    val baseUrlDraft: String = "",
    val modelDraft: String = "",
    val dirty: Boolean = false,
    val temperature: Float = 0.35f,
    val contextEnabled: Boolean = true,
    val contextLimit: Int = 6,
    val ttsSpeed: Float = 0.95f,
    val ttsAutoPlay: Boolean = false,
    val autoTranslateTranscript: Boolean = false,
    val defaultStyle: ReplyStyle = ReplyStyle.NATURAL,
    val hasKey: Boolean = false,
    val keyEncrypted: Boolean = true,
    val keyDraft: String = "",
    val keyVisible: Boolean = false,
    val testing: ConnectionTest = ConnectionTest.Idle,
    val stats: LibraryStats = LibraryStats(),
    val tts: TtsState = TtsState(),
    val message: String? = null,
    val clearing: Boolean = false,
    // ---- on-device engine ----------------------------------------------------
    val engineAvailable: Boolean = true,
    val engine: LocalModelState = LocalModelState.Idle,
    val generatedTokens: Int = 0,
    val deviceModels: List<LocalModelFile> = emptyList(),
    val deviceModel: String = "",
    val downloads: Map<String, DownloadState> = emptyMap(),
    val importing: Boolean = false,
    val freeStorage: Long = 0L,
    val gpuAvailable: Boolean = false,
    val gpuDevice: String = "",
    val gpuOffload: Boolean = false,
) {
    val preset: ProviderPreset get() = ProviderPresets.byId(presetId)
    val onDevice: Boolean get() = presetId == ProviderPresets.device.id
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update { current ->
                    if (current.dirty) {
                        current.copy(
                            presetId = settings.presetId,
                            temperature = settings.temperature,
                            contextEnabled = settings.contextEnabled,
                            contextLimit = settings.contextLimit,
                            ttsSpeed = settings.ttsSpeed,
                            ttsAutoPlay = settings.ttsAutoPlay,
                            autoTranslateTranscript = settings.autoTranslateTranscript,
                            defaultStyle = settings.defaultStyle,
                        )
                    } else {
                        current.copy(
                            presetId = settings.presetId,
                            baseUrl = settings.baseUrl,
                            model = settings.model,
                            baseUrlDraft = settings.baseUrl,
                            modelDraft = settings.model,
                            temperature = settings.temperature,
                            contextEnabled = settings.contextEnabled,
                            contextLimit = settings.contextLimit,
                            ttsSpeed = settings.ttsSpeed,
                            ttsAutoPlay = settings.ttsAutoPlay,
                            autoTranslateTranscript = settings.autoTranslateTranscript,
                            defaultStyle = settings.defaultStyle,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            container.library.observeStats().collect { stats ->
                _state.update { it.copy(stats = stats) }
            }
        }
        viewModelScope.launch {
            container.speech.state.collect { tts ->
                _state.update { it.copy(tts = tts) }
            }
        }
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(deviceModel = settings.deviceModel, gpuOffload = settings.gpuOffload)
                }
            }
        }
        viewModelScope.launch {
            // Enumerating devices wakes the ggml backend, so keep it off the main
            // thread and only ask once per screen.
            val available = withContext(Dispatchers.IO) { container.localEngine.gpuAvailable }
            val device = if (available) {
                withContext(Dispatchers.IO) { container.localEngine.gpuDeviceName }
            } else {
                ""
            }
            _state.update { it.copy(gpuAvailable = available, gpuDevice = device) }
        }
        viewModelScope.launch {
            container.localEngine.state.collect { engine ->
                _state.update { it.copy(engine = engine) }
            }
        }
        viewModelScope.launch {
            container.localEngine.tokens.collect { tokens ->
                _state.update { it.copy(generatedTokens = tokens) }
            }
        }
        viewModelScope.launch {
            container.downloads.states.collect { downloads ->
                _state.update { it.copy(downloads = downloads) }
                val finished = downloads.values.filterIsInstance<DownloadState.Done>()
                if (finished.isNotEmpty()) refreshModels()
            }
        }
        refreshModels()
        refreshKeyState()
    }

    // ---- on-device models ----------------------------------------------------

    fun refreshModels() {
        _state.update {
            it.copy(
                engineAvailable = container.localEngine.isAvailable,
                deviceModels = container.models.list(),
                freeStorage = container.models.freeBytes(),
            )
        }
    }

    fun selectDeviceModel(fileName: String) {
        viewModelScope.launch {
            if (_state.value.deviceModel != fileName) {
                container.localEngine.unload()
                container.localEngine.clearGpuRejection()
            }
            container.settings.setDeviceModel(fileName)
            show("$fileName is the on-device model")
        }
    }

    fun deleteDeviceModel(fileName: String) {
        if (_state.value.engine is LocalModelState.Ready &&
            (_state.value.engine as LocalModelState.Ready).fileName == fileName
        ) {
            container.localEngine.unload()
        }
        val removed = container.models.delete(fileName)
        if (removed && _state.value.deviceModel == fileName) {
            viewModelScope.launch { container.settings.setDeviceModel("") }
        }
        refreshModels()
        show(if (removed) "Model deleted" else "The file could not be deleted")
    }

    fun downloadModel(source: LocalModelSource) {
        container.downloads.start(source)
        show("Downloading ${source.name}")
    }

    fun cancelDownload(sourceId: String) {
        container.downloads.cancel(sourceId)
    }

    fun importModel(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                val imported = withContext(Dispatchers.IO) { container.models.import(uri) }
                container.settings.setDeviceModel(imported.name)
                refreshModels()
                show("${imported.name} imported")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e.message?.take(120) ?: "The import failed")
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    fun setGpuOffload(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setGpuOffload(enabled)
            container.localEngine.clearGpuRejection()
            // Layers are placed when the model is loaded, so an already loaded
            // model keeps its old placement until it is loaded again.
            if (_state.value.engine !is LocalModelState.Idle) container.localEngine.unload()
            show(
                if (enabled && !_state.value.gpuAvailable) {
                    "This build has no GPU backend, so models stay on the CPU"
                } else if (enabled) {
                    "GPU offload on — load the model to apply"
                } else {
                    "GPU offload off — load the model to apply"
                },
            )
        }
    }

    fun loadDeviceModel() {
        val fileName = _state.value.deviceModel
        if (fileName.isBlank()) {
            show("Choose a model first")
            return
        }
        viewModelScope.launch {
            try {
                container.localEngine.preload(
                    file = container.models.file(fileName),
                    offload = _state.value.gpuOffload,
                )
                show("$fileName loaded")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show((e as? AiException)?.message ?: "The model could not be loaded")
            }
        }
    }

    fun unloadDeviceModel() {
        container.localEngine.unload()
        show("Model unloaded")
    }

    fun stopDeviceGeneration() {
        container.localEngine.cancel()
    }

    fun refreshKeyState() {
        _state.update {
            it.copy(hasKey = container.keys.apiKey.isNotBlank(), keyEncrypted = container.keys.isEncrypted)
        }
    }

    fun selectPreset(preset: ProviderPreset) {
        _state.update {
            it.copy(
                presetId = preset.id,
                baseUrlDraft = preset.baseUrl,
                modelDraft = preset.model,
                dirty = true,
                testing = ConnectionTest.Idle,
            )
        }
    }

    fun setBaseUrlDraft(value: String) {
        _state.update { it.copy(baseUrlDraft = value, dirty = true, testing = ConnectionTest.Idle) }
    }

    fun setModelDraft(value: String) {
        _state.update { it.copy(modelDraft = value, dirty = true, testing = ConnectionTest.Idle) }
    }

    fun saveProvider() {
        val current = _state.value
        viewModelScope.launch {
            container.settings.setProvider(
                presetId = current.presetId,
                baseUrl = current.baseUrlDraft.trim(),
                model = current.modelDraft.trim(),
            )
            _state.update { it.copy(dirty = false) }
            show("Model settings saved")
        }
    }

    fun setKeyDraft(value: String) {
        _state.update { it.copy(keyDraft = value) }
    }

    fun toggleKeyVisible() {
        _state.update { it.copy(keyVisible = !it.keyVisible) }
    }

    fun saveKey() {
        val value = _state.value.keyDraft.trim()
        if (value.isEmpty()) {
            show("Paste the key first")
            return
        }
        container.keys.apiKey = value
        _state.update {
            it.copy(hasKey = true, keyDraft = "", keyVisible = false, testing = ConnectionTest.Idle)
        }
        show("Key saved on this device")
    }

    fun clearKey() {
        container.keys.clear()
        _state.update { it.copy(hasKey = false, testing = ConnectionTest.Idle) }
        show("Key removed")
    }

    fun testConnection() {
        if (_state.value.hasKey.not()) {
            _state.update {
                it.copy(testing = ConnectionTest.Failed(AiFailure.NO_KEY.headline, AiFailure.NO_KEY.detail))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = ConnectionTest.Testing) }
            try {
                val reply = container.translations.testConnection()
                _state.update {
                    it.copy(
                        testing = ConnectionTest.Success(
                            if (reply.isBlank()) "The endpoint answered." else "Endpoint replied: ${reply.take(80)}",
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = (e as? AiException)?.failure ?: AiFailure.UNKNOWN
                _state.update {
                    it.copy(
                        testing = ConnectionTest.Failed(
                            headline = failure.headline,
                            detail = listOfNotNull(failure.detail, e.message?.take(160)).joinToString(" · "),
                        ),
                    )
                }
            }
        }
    }

    fun setContextEnabled(value: Boolean) {
        viewModelScope.launch { container.settings.setContextEnabled(value) }
    }

    fun setContextLimit(value: Int) {
        viewModelScope.launch { container.settings.setContextLimit(value) }
    }

    fun setTtsSpeed(value: Float) {
        viewModelScope.launch { container.settings.setTtsSpeed(value) }
    }

    fun setTtsAutoPlay(value: Boolean) {
        viewModelScope.launch { container.settings.setTtsAutoPlay(value) }
    }

    fun setAutoTranslateTranscript(value: Boolean) {
        viewModelScope.launch { container.settings.setAutoTranslateTranscript(value) }
    }

    fun setDefaultStyle(style: ReplyStyle) {
        viewModelScope.launch { container.settings.setDefaultStyle(style) }
    }

    fun testVoice() {
        val state = _state.value
        container.speech.speak(
            "Yarın görüşürüz. İyi akşamlar.",
            "settings-voice-test",
            state.ttsSpeed,
        )
    }

    fun stopVoice() = container.speech.stop()

    fun clearAllData() {
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            container.translations.clearEverything()
            container.keys.clear()
            _state.update {
                it.copy(
                    clearing = false,
                    hasKey = false,
                    baseUrlDraft = it.baseUrl,
                    modelDraft = it.model,
                )
            }
            show("All saved data and the key were removed")
        }
    }

    fun consumeMessage() {
        messageJob?.cancel()
        _state.update { it.copy(message = null) }
    }

    private fun show(text: String) {
        _state.update { it.copy(message = text) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2800)
            _state.update { it.copy(message = null) }
        }
    }

}
