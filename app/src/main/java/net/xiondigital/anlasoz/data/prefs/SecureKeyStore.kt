package net.xiondigital.anlasoz.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the provider key in EncryptedSharedPreferences (AES256-GCM values,
 * AES256-SIV keys, master key in the Android Keystore).
 *
 * If a device cannot create a Keystore-backed master key, this falls back to
 * app-private preferences and reports [isEncrypted] as false so Settings can
 * tell the user instead of silently pretending the key is protected.
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private val prefs: SharedPreferences =
        encryptedPrefs ?: appContext.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)

    val isEncrypted: Boolean
        get() = encryptedPrefs != null

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_API, value.trim()).apply()
        }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    private companion object {
        const val FILE_NAME = "anlasoz_secure"
        const val FALLBACK_FILE = "anlasoz_fallback"
        const val KEY_API = "api_key"
    }
}
