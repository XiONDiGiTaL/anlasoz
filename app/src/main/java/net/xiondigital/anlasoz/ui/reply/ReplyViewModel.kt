package net.xiondigital.anlasoz.ui.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.ai.AiException
import net.xiondigital.anlasoz.ai.AiFailure
import net.xiondigital.anlasoz.ai.ProviderPresets
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.ReplyStyle
import net.xiondigital.anlasoz.ui.translate.TranslateStatus

data class ReplyUiState(
    val input: String = "",
    val style: ReplyStyle = ReplyStyle.NATURAL,
    val status: TranslateStatus = TranslateStatus.IDLE,
    val result: Analysis? = null,
    val failure: AiFailure? = null,
    val failureDetail: String? = null,
    val configured: Boolean = false,
    val threadId: Long = 0,
    val ttsAutoPlay: Boolean = false,
    val ttsSpeed: Float = 0.95f,
    val message: String? = null,
)

class ReplyViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReplyUiState())
    val state: StateFlow<ReplyUiState> = _state.asStateFlow()

    val speech = container.speech
    private var messageJob: Job? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(
                        style = if (it.status == TranslateStatus.IDLE) settings.defaultStyle else it.style,
                        ttsAutoPlay = settings.ttsAutoPlay,
                        ttsSpeed = settings.ttsSpeed,
                    )
                }
                refreshConfig()
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(threadId = container.translations.activeThread().id) }
        }
    }

    suspend fun refreshConfig() {
        val settings = container.settings.settings.first()
        val hasKey = container.keys.apiKey.isNotBlank()
        _state.update {
            it.copy(
                configured = if (settings.presetId == ProviderPresets.device.id) {
                    settings.deviceModel.isNotBlank()
                } else {
                    hasKey && settings.baseUrl.isNotBlank() && settings.model.isNotBlank()
                },
            )
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun selectStyle(style: ReplyStyle) {
        _state.update { it.copy(style = style) }
        viewModelScope.launch { container.settings.setDefaultStyle(style) }
    }

    fun generate() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            show("Write what you want to say in English first")
            return
        }
        if (!_state.value.configured) {
            _state.update { it.copy(status = TranslateStatus.SETUP) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(status = TranslateStatus.LOADING, failure = null, failureDetail = null)
            }
            val threadId = _state.value.threadId.takeIf { it > 0 }
                ?: container.translations.activeThread().id
            try {
                val result = container.translations.composeReply(text, _state.value.style, threadId)
                _state.update {
                    it.copy(
                        status = TranslateStatus.RESULT,
                        result = result,
                        threadId = threadId,
                    )
                }
                if (_state.value.ttsAutoPlay) {
                    container.speech.speak(
                        result.naturalTranslation,
                        "reply-${result.id}",
                        _state.value.ttsSpeed,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ai = e as? AiException
                _state.update {
                    it.copy(
                        status = if (ai?.failure == AiFailure.NO_KEY ||
                            ai?.failure == AiFailure.NOT_CONFIGURED
                        ) {
                            TranslateStatus.SETUP
                        } else {
                            TranslateStatus.ERROR
                        },
                        failure = ai?.failure ?: AiFailure.UNKNOWN,
                        failureDetail = e.message,
                    )
                }
            }
        }
    }

    fun retry() = generate()

    fun clear() {
        _state.update {
            it.copy(
                input = "",
                result = null,
                status = TranslateStatus.IDLE,
                failure = null,
                failureDetail = null,
            )
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
            delay(2600)
            _state.update { it.copy(message = null) }
        }
    }
}
