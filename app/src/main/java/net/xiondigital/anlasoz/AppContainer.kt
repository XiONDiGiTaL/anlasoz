package net.xiondigital.anlasoz

import android.content.Context
import net.xiondigital.anlasoz.ai.LocalEngine
import net.xiondigital.anlasoz.audio.SpeechController
import net.xiondigital.anlasoz.data.db.AppDatabase
import net.xiondigital.anlasoz.data.export.ExportManager
import net.xiondigital.anlasoz.data.prefs.SecureKeyStore
import net.xiondigital.anlasoz.data.prefs.SettingsStore
import net.xiondigital.anlasoz.data.repo.LibraryRepository
import net.xiondigital.anlasoz.data.repo.TranslationRepository
import net.xiondigital.anlasoz.local.ModelDownloader
import net.xiondigital.anlasoz.local.ModelStore

/** Manual dependency container: one instance per process. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val settings: SettingsStore = SettingsStore(context)
    val keys: SecureKeyStore = SecureKeyStore(context)
    val speech: SpeechController = SpeechController(context)
    val exporter: ExportManager = ExportManager(context)
    val models: ModelStore = ModelStore(context.filesDir, context.contentResolver)
    val downloads: ModelDownloader = ModelDownloader(models)
    val localEngine: LocalEngine = LocalEngine()
    val translations: TranslationRepository =
        TranslationRepository(database, settings, keys, localEngine, models)
    val library: LibraryRepository = LibraryRepository(database)
}
