package net.xiondigital.anlasoz.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class SttState(
    val listening: Boolean = false,
    val partial: String = "",
    val rms: Float = 0f,
    val error: String? = null,
    val available: Boolean = true,
)

/**
 * Speech-to-text driven by Android's SpeechRecognizer.
 *
 * Audio only starts after an explicit tap, never in the background, and every
 * recognizer error is translated into an actionable message.
 */
class SttController(private val context: Context) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var locale: Locale = Locale("tr", "TR")

    private val _state = MutableStateFlow(SttState(available = isAvailable(context)))
    val state: StateFlow<SttState> = _state

    /** Called with (text, isFinal) as the recognizer produces results. */
    var onTranscript: ((String, Boolean) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    fun updateLocale(locale: Locale) {
        this.locale = locale
    }

    fun start() {
        if (!isAvailable(context)) {
            _state.value = SttState(
                available = false,
                error = speechUnavailableMessage(),
            )
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        _state.value = SttState(listening = true, available = true)
        runCatching { engine.startListening(intent) }.onFailure {
            _state.value = SttState(
                available = true,
                error = "The recognizer could not start. Close other dictation apps and try again.",
            )
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        _state.value = _state.value.copy(listening = false)
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        _state.value = SttState(available = isAvailable(context))
        onFinished?.invoke()
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    // ---- RecognitionListener ---------------------------------------------------

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = _state.value.copy(listening = true, error = null)
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        _state.value = _state.value.copy(rms = rmsdB.coerceIn(0f, 10f))
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        _state.value = _state.value.copy(listening = false)
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "The microphone could not capture audio."
            SpeechRecognizer.ERROR_CLIENT -> "Recognition was interrupted. Try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required for transcription."
            SpeechRecognizer.ERROR_NETWORK -> "Speech recognition needs a network connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "I could not catch that. Try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "The recognizer is busy. Wait a moment and try again."
            SpeechRecognizer.ERROR_SERVER -> "The speech server returned an error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many recognition requests. Wait a moment."
            else -> "Speech recognition failed."
        }
        _state.value = SttState(available = true, error = message)
        onFinished?.invoke()
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) onTranscript?.invoke(text, true)
        _state.value = SttState(available = true)
        onFinished?.invoke()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            _state.value = _state.value.copy(partial = text)
            onTranscript?.invoke(text, false)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        fun speechUnavailableMessage(): String =
            "This device has no speech recognition service. Type or paste the message instead, or install Google's speech services."

        /** Opens the system speech-input settings as a fallback path. */
        fun settingsIntent(): Intent =
            Intent("com.android.settings.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
