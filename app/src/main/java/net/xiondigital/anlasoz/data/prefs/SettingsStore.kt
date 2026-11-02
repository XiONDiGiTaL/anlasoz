package net.xiondigital.anlasoz.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.xiondigital.anlasoz.ai.ProviderPresets
import net.xiondigital.anlasoz.data.model.ReplyStyle

private val Context.dataStore by preferencesDataStore(name = "anlasoz_settings")

data class AppSettings(
    val presetId: String = ProviderPresets.freebuff.id,
    val baseUrl: String = ProviderPresets.freebuff.baseUrl,
    val model: String = ProviderPresets.freebuff.model,
    val temperature: Float = 0.35f,
    val contextEnabled: Boolean = true,
    val contextLimit: Int = 6,
    val ttsSpeed: Float = 0.95f,
    val ttsAutoPlay: Boolean = false,
    val autoTranslateTranscript: Boolean = false,
    val defaultStyle: ReplyStyle = ReplyStyle.NATURAL,
    val activeThreadId: Long = 0L,
    val preferLocalPronunciation: Boolean = true,
    /** File name inside the app's models directory, used when the preset is "device". */
    val deviceModel: String = "",
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            presetId = prefs[Keys.PRESET] ?: ProviderPresets.freebuff.id,
            baseUrl = prefs[Keys.BASE_URL] ?: ProviderPresets.freebuff.baseUrl,
            model = prefs[Keys.MODEL] ?: ProviderPresets.freebuff.model,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.35f,
            contextEnabled = prefs[Keys.CONTEXT] ?: true,
            contextLimit = prefs[Keys.CONTEXT_LIMIT] ?: 6,
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: 0.95f,
            ttsAutoPlay = prefs[Keys.TTS_AUTO_PLAY] ?: false,
            autoTranslateTranscript = prefs[Keys.AUTO_TRANSLATE] ?: false,
            defaultStyle = ReplyStyle.from(prefs[Keys.DEFAULT_STYLE]),
            activeThreadId = prefs[Keys.ACTIVE_THREAD] ?: 0L,
            preferLocalPronunciation = prefs[Keys.LOCAL_PRONUNCIATION] ?: true,
            deviceModel = prefs[Keys.DEVICE_MODEL] ?: "",
        )
    }

    suspend fun setPreset(id: String, baseUrl: String, model: String) {
        context.dataStore.edit {
            it[Keys.PRESET] = id
            it[Keys.BASE_URL] = baseUrl
            it[Keys.MODEL] = model
        }
    }

    suspend fun setProvider(presetId: String, baseUrl: String, model: String) =
        setPreset(presetId, baseUrl, model)

    suspend fun setBaseUrl(value: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = value.trim() }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[Keys.MODEL] = value.trim() }
    }

    suspend fun setTemperature(value: Float) {
        context.dataStore.edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 1f) }
    }

    suspend fun setContextEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CONTEXT] = value }
    }

    suspend fun setContextLimit(value: Int) {
        context.dataStore.edit { it[Keys.CONTEXT_LIMIT] = value.coerceIn(0, 20) }
    }

    suspend fun setTtsSpeed(value: Float) {
        context.dataStore.edit { it[Keys.TTS_SPEED] = value.coerceIn(0.5f, 1.6f) }
    }

    suspend fun setTtsAutoPlay(value: Boolean) {
        context.dataStore.edit { it[Keys.TTS_AUTO_PLAY] = value }
    }

    suspend fun setAutoTranslateTranscript(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_TRANSLATE] = value }
    }

    suspend fun setDefaultStyle(style: ReplyStyle) {
        context.dataStore.edit { it[Keys.DEFAULT_STYLE] = style.id }
    }

    suspend fun setActiveThread(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_THREAD] = id }
    }

    suspend fun setPreferLocalPronunciation(value: Boolean) {
        context.dataStore.edit { it[Keys.LOCAL_PRONUNCIATION] = value }
    }

    suspend fun setDeviceModel(fileName: String) {
        context.dataStore.edit { it[Keys.DEVICE_MODEL] = fileName }
    }

    private object Keys {
        val PRESET = stringPreferencesKey("provider_preset")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val CONTEXT = booleanPreferencesKey("context_enabled")
        val CONTEXT_LIMIT = intPreferencesKey("context_limit")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_AUTO_PLAY = booleanPreferencesKey("tts_auto_play")
        val AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        val DEFAULT_STYLE = stringPreferencesKey("default_style")
        val ACTIVE_THREAD = longPreferencesKey("active_thread")
        val LOCAL_PRONUNCIATION = booleanPreferencesKey("local_pronunciation")
        val DEVICE_MODEL = stringPreferencesKey("device_model")
    }
}
