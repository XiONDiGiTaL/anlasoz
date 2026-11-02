package net.xiondigital.anlasoz.audio

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.xiondigital.anlasoz.core.TurkishText
import java.util.Locale

data class TtsState(
    val ready: Boolean = false,
    val turkishAvailable: Boolean = false,
    val engineName: String? = null,
    val speaking: Boolean = false,
    val paused: Boolean = false,
    val speakingKey: String? = null,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val rate: Float = 0.95f,
    val message: String? = null,
)

/**
 * Turkish text-to-speech for around the app.
 *
 * The voice is always tr-TR: if the device has no Turkish voice, the controller
 * refuses to speak rather than reading Turkish with an English voice.
 * Pause/resume is emulated sentence by sentence, which works on every engine.
 */
class SpeechController(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var queue: List<String> = emptyList()
    private var queueKey: String? = null
    private var sentenceIndex = 0
    private var pausedAt: Int? = null

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state

    init {
        startEngine()
    }

    private fun startEngine() {
        if (tts == null) tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.update {
                it.copy(
                    ready = false,
                    message = "No text-to-speech engine is available on this device.",
                )
            }
            return
        }
        val engine = tts ?: return
        engine.setOnUtteranceProgressListener(progress)

        val turkish = Locale("tr", "TR")
        val availability = engine.isLanguageAvailable(turkish)
        val supported = availability == TextToSpeech.LANG_AVAILABLE ||
            availability == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            availability == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

        if (supported) {
            engine.language = turkish
            pickTurkishVoice(engine)
        }

        _state.update {
            it.copy(
                ready = true,
                turkishAvailable = supported,
                engineName = runCatching { engine.defaultEngine }.getOrNull(),
                message = if (supported) null else
                    "Turkish (tr-TR) is not installed for this speech engine. Install Turkish voice data to hear messages.",
            )
        }
    }

    private fun pickTurkishVoice(engine: TextToSpeech) {
        val voices = runCatching { engine.voices }.getOrNull() ?: return
        val turkish = voices.filter { it.locale.language.equals("tr", ignoreCase = true) }
        if (turkish.isEmpty()) return
        val preferred = turkish.firstOrNull {
            it.quality >= Voice.QUALITY_HIGH && !it.isNetworkConnectionRequired
        } ?: turkish.firstOrNull { !it.isNetworkConnectionRequired } ?: turkish.first()
        runCatching { engine.voice = preferred }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val index = indexOf(utteranceId) ?: return
            sentenceIndex = index
            _state.update { it.copy(speaking = true, sentenceIndex = index) }
        }

        override fun onDone(utteranceId: String?) {
            val index = indexOf(utteranceId) ?: return
            if (index >= queue.lastIndex) {
                pausedAt = null
                _state.update {
                    it.copy(
                        speaking = false,
                        paused = false,
                        speakingKey = null,
                        sentenceIndex = 0,
                        sentenceCount = 0,
                    )
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.update {
                it.copy(speaking = false, message = "The speech engine could not read this text.")
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _state.update {
                it.copy(speaking = false, message = "The speech engine could not read this text.")
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (interrupted) _state.update { it.copy(speaking = false) }
        }
    }

    private fun indexOf(utteranceId: String?): Int? =
        utteranceId?.substringAfterLast('#')?.toIntOrNull()

    fun speak(text: String, key: String, rate: Float = _state.value.rate) {
        val engine = tts
        if (engine == null) {
            startEngine()
            _state.update { it.copy(message = "Preparing the Turkish voice — tap again in a moment.") }
            return
        }
        val current = _state.value
        if (!current.ready) {
            _state.update { it.copy(message = "Preparing the Turkish voice — tap again in a moment.") }
            return
        }
        if (!current.turkishAvailable) {
            _state.update {
                it.copy(
                    message = "Turkish (tr-TR) is not installed. Install Turkish voice data to hear messages.",
                )
            }
            return
        }
        val parts = TurkishText.sentences(TurkishText.normalizeForSpeech(text))
            .ifEmpty { listOf(TurkishText.normalizeForSpeech(text)) }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return

        engine.stop()
        queue = parts
        queueKey = key
        sentenceIndex = 0
        pausedAt = null
        engine.setSpeechRate(rate.coerceIn(0.5f, 1.6f))
        _state.update {
            it.copy(
                speaking = true,
                paused = false,
                speakingKey = key,
                sentenceIndex = 0,
                sentenceCount = parts.size,
                rate = rate,
                message = null,
            )
        }
        parts.forEachIndexed { index, sentence ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(sentence, mode, null, "$key#$index")
        }
    }

    fun pause() {
        if (!_state.value.speaking) return
        pausedAt = sentenceIndex
        tts?.stop()
        _state.update { it.copy(speaking = false, paused = true) }
    }

    fun resume() {
        val engine = tts ?: return
        val start = pausedAt ?: return
        val key = queueKey ?: return
        if (start >= queue.size) {
            stop()
            return
        }
        pausedAt = null
        _state.update {
            it.copy(
                speaking = true,
                paused = false,
                speakingKey = key,
                sentenceIndex = start,
                sentenceCount = queue.size,
            )
        }
        queue.drop(start).forEachIndexed { offset, sentence ->
            val index = start + offset
            val mode = if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(sentence, mode, null, "$key#$index")
        }
    }

    fun stop() {
        tts?.stop()
        queue = emptyList()
        queueKey = null
        pausedAt = null
        _state.update {
            it.copy(
                speaking = false,
                paused = false,
                speakingKey = null,
                sentenceIndex = 0,
                sentenceCount = 0,
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** Opens the system text-to-speech settings screen, where voice data is installed. */
        fun ttsSettingsIntent(): Intent =
            Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Asks the current engine to install its voice data. */
        fun installVoiceDataIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
