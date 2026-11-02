package net.xiondigital.anlasoz

import android.content.Context
import net.xiondigital.anlasoz.audio.SpeechController
import net.xiondigital.anlasoz.data.db.AppDatabase
import net.xiondigital.anlasoz.data.export.ExportManager
import net.xiondigital.anlasoz.data.prefs.SecureKeyStore
import net.xiondigital.anlasoz.data.prefs.SettingsStore
import net.xiondigital.anlasoz.data.repo.LibraryRepository
import net.xiondigital.anlasoz.data.repo.TranslationRepository

/** Manual dependency container: one instance per process. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val settings: SettingsStore = SettingsStore(context)
    val keys: SecureKeyStore = SecureKeyStore(context)
    val speech: SpeechController = SpeechController(context)
    val exporter: ExportManager = ExportManager(context)
    val translations: TranslationRepository =
        TranslationRepository(database, settings, keys)
    val library: LibraryRepository = LibraryRepository(database)
}
