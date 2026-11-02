package net.xiondigital.anlasoz.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.ai.AiException
import net.xiondigital.anlasoz.ai.AiFailure
import net.xiondigital.anlasoz.core.TurkishText
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.data.model.MessageThread
import net.xiondigital.anlasoz.data.model.VocabItem

enum class TranslateStatus { IDLE, LOADING, RESULT, ERROR, SETUP }

data class TranslateUiState(
    val input: String = "",
    val status: TranslateStatus = TranslateStatus.IDLE,
    val analysis: Analysis? = null,
    val failure: AiFailure? = null,
    val failureDetail: String? = null,
    val threadId: Long = 0,
    val threads: List<MessageThread> = emptyList(),
    val configured: Boolean = false,
    val hasKey: Boolean = false,
    val savedWords: Set<String> = emptySet(),
    val hintLanguage: String = "unknown",
    val contextEnabled: Boolean = true,
    val ttsAutoPlay: Boolean = false,
    val autoTranslateTranscript: Boolean = false,
    val ttsSpeed: Float = 0.95f,
    val message: String? = null,
) {
    val threadTitle: String
        get() = threads.firstOrNull { it.id == threadId }?.title ?: "Conversation"
}

class TranslateViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    val speech = container.speech

    private var messageJob: Job? = null
    private var analyzeJob: Job? = null
    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(
                        contextEnabled = settings.contextEnabled,
                        ttsAutoPlay = settings.ttsAutoPlay,
                        autoTranslateTranscript = settings.autoTranslateTranscript,
                        ttsSpeed = settings.ttsSpeed,
                    )
                }
                refreshConfig()
            }
        }
        viewModelScope.launch {
            container.translations.observeThreads().collect { threads ->
                _state.update { it.copy(threads = threads) }
            }
        }
        viewModelScope.launch {
            container.library.observeVocabulary().collect { vocabulary ->
                _state.update {
                    it.copy(savedWords = vocabulary.map { word -> word.turkish.lowercase() }.toSet())
                }
            }
        }
        viewModelScope.launch {
            val thread = container.translations.activeThread()
            loadThread(thread.id)
        }
    }

    suspend fun refreshConfig() {
        val settings = container.settings.settings.first()
        val hasKey = container.keys.apiKey.isNotBlank()
        _state.update {
            it.copy(
                hasKey = hasKey,
                configured = hasKey && settings.baseUrl.isNotBlank() && settings.model.isNotBlank(),
            )
        }
    }

    fun onInputChange(text: String) {
        _state.update {
            it.copy(
                input = text,
                hintLanguage = TurkishText.guessLanguage(text),
                status = if (it.status == TranslateStatus.SETUP) TranslateStatus.IDLE else it.status,
            )
        }
    }

    fun onSharedText(text: String) {
        onInputChange(text)
        if (_state.value.configured) analyze()
    }

    fun clearInput() {
        _state.update {
            it.copy(
                input = "",
                status = TranslateStatus.IDLE,
                analysis = null,
                failure = null,
                failureDetail = null,
                hintLanguage = "unknown",
            )
        }
    }

    fun analyze() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            show("Type or paste a Turkish or English message first")
            return
        }
        if (!_state.value.configured) {
            _state.update { it.copy(status = TranslateStatus.SETUP) }
            return
        }
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _state.update {
                it.copy(status = TranslateStatus.LOADING, failure = null, failureDetail = null)
            }
            val threadId = _state.value.threadId.takeIf { it > 0 }
                ?: container.translations.activeThread().id
            try {
                val analysis = container.translations.analyze(text, threadId)
                _state.update {
                    it.copy(
                        status = TranslateStatus.RESULT,
                        analysis = analysis,
                        threadId = threadId,
                        failure = null,
                        failureDetail = null,
                    )
                }
                if (_state.value.ttsAutoPlay) {
                    val turkish = if (analysis.direction == Direction.TR_EN) {
                        analysis.sourceText
                    } else {
                        analysis.naturalTranslation
                    }
                    container.speech.speak(
                        turkish,
                        "analysis-${analysis.id}",
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

    fun retry() = analyze()

    fun toggleFavorite() {
        val analysis = _state.value.analysis ?: return
        val target = !analysis.favorite
        viewModelScope.launch {
            container.translations.setFavorite(analysis.id, target)
            _state.update { current ->
                current.copy(analysis = current.analysis?.takeIf { it.id == analysis.id }?.copy(favorite = target)
                    ?: current.analysis)
            }
            show(if (target) "Saved to favorites" else "Removed from favorites")
        }
    }

    fun saveWord(item: VocabItem) {
        viewModelScope.launch {
            val ok = container.library.saveVocabulary(item, _state.value.analysis?.id ?: 0L)
            show(
                if (ok) "Saved \"${item.turkish}\" to vocabulary"
                else "\"${item.turkish}\" is already in your vocabulary",
            )
        }
    }

    fun switchThread(id: Long) {
        viewModelScope.launch {
            container.translations.setActiveThread(id)
            loadThread(id)
        }
    }

    fun newThread() {
        viewModelScope.launch {
            val thread = container.translations.createThread("New conversation")
            loadThread(thread.id)
            show("Started ${thread.title}")
        }
    }

    fun deleteThread(id: Long) {
        viewModelScope.launch {
            container.translations.deleteThread(id)
            val next = container.translations.activeThread()
            loadThread(next.id)
            show("Conversation deleted")
        }
    }

    fun useTranscript(text: String) {
        onInputChange(text)
        if (_state.value.autoTranslateTranscript && _state.value.configured) analyze()
    }

    fun consumeMessage() {
        messageJob?.cancel()
        _state.update { it.copy(message = null) }
    }

    private fun loadThread(threadId: Long) {
        if (threadId <= 0L) return
        _state.update { it.copy(threadId = threadId) }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            container.translations.observeThreadTranslations(threadId).collect { messages ->
                val latest = messages.lastOrNull()
                _state.update { current ->
                    if (current.threadId != threadId) return@update current
                    if (current.status == TranslateStatus.LOADING) return@update current
                    current.copy(
                        analysis = latest,
                        status = if (latest != null) TranslateStatus.RESULT else TranslateStatus.IDLE,
                        failure = null,
                        failureDetail = null,
                    )
                }
            }
        }
    }

    private fun show(text: String) {
        _state.update { it.copy(message = text) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2600)
            _state.update { it.copy(message = null) }
        }
    }
}
