package net.xiondigital.anlasoz.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.xiondigital.anlasoz.ai.AiClient
import net.xiondigital.anlasoz.ai.AiException
import net.xiondigital.anlasoz.ai.AiFailure
import net.xiondigital.anlasoz.ai.AnalysisParser
import net.xiondigital.anlasoz.ai.ChatMessage
import net.xiondigital.anlasoz.ai.LocalEngine
import net.xiondigital.anlasoz.ai.Prompts
import net.xiondigital.anlasoz.ai.ProviderPresets
import net.xiondigital.anlasoz.core.TurkishText
import net.xiondigital.anlasoz.data.db.AnalysisEntity
import net.xiondigital.anlasoz.data.db.AppDatabase
import net.xiondigital.anlasoz.data.db.GrammarNoteEntity
import net.xiondigital.anlasoz.data.db.ThreadEntity
import net.xiondigital.anlasoz.data.db.toDomain
import net.xiondigital.anlasoz.data.db.toEntity
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.AnalysisResult
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.data.model.MessageThread
import net.xiondigital.anlasoz.data.model.ReplyResult
import net.xiondigital.anlasoz.data.model.ReplyStyle
import net.xiondigital.anlasoz.data.prefs.AppSettings
import net.xiondigital.anlasoz.data.prefs.SecureKeyStore
import net.xiondigital.anlasoz.data.prefs.SettingsStore
import net.xiondigital.anlasoz.local.ModelStore

/**
 * Orchestrates one translation turn: conversation context, model call, tolerant
 * parsing (with one repair round), persistence and grammar capture.
 */
class TranslationRepository(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val keyStore: SecureKeyStore,
    private val localEngine: LocalEngine,
    private val modelStore: ModelStore,
    private val ai: AiClient = AiClient(),
    private val parser: AnalysisParser = AnalysisParser(),
) {

    // ---- threads ---------------------------------------------------------------

    fun observeThreads(): Flow<List<MessageThread>> =
        db.threadDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun activeThread(): MessageThread {
        val settings = settingsStore.settings.first()
        val existing = settings.activeThreadId.takeIf { it > 0 }?.let { db.threadDao().byId(it) }
        if (existing != null) return existing.toDomain()
        val first = db.threadDao().all().firstOrNull()
        if (first != null) {
            settingsStore.setActiveThread(first.id)
            return first.toDomain()
        }
        val now = System.currentTimeMillis()
        val id = db.threadDao().insert(ThreadEntity(title = "General Turkish", createdAt = now, updatedAt = now))
        settingsStore.setActiveThread(id)
        return MessageThread(id, "General Turkish", now, now)
    }

    suspend fun setActiveThread(id: Long) = settingsStore.setActiveThread(id)

    suspend fun createThread(title: String): MessageThread {
        val now = System.currentTimeMillis()
        val name = title.trim().ifBlank { "New conversation" }
        val id = db.threadDao().insert(ThreadEntity(title = name, createdAt = now, updatedAt = now))
        settingsStore.setActiveThread(id)
        return MessageThread(id, name, now, now)
    }

    suspend fun renameThread(id: Long, title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        db.threadDao().rename(id, name, System.currentTimeMillis())
    }

    suspend fun deleteThread(id: Long) {
        db.analysisDao().deleteThread(id)
        db.threadDao().delete(id)
        val settings = settingsStore.settings.first()
        if (settings.activeThreadId == id) {
            val next = db.threadDao().all().firstOrNull()
            settingsStore.setActiveThread(next?.id ?: 0L)
        }
    }

    fun observeThreadMessages(threadId: Long): Flow<List<Analysis>> =
        db.analysisDao().observeThread(threadId).map { list -> list.map { it.toDomain() } }

    /** Analyze-flow messages only, so reply drafts never appear on the Translate screen. */
    fun observeThreadTranslations(threadId: Long): Flow<List<Analysis>> =
        db.analysisDao().observeThreadTranslations(threadId).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 200): Flow<List<Analysis>> =
        db.analysisDao().observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeHistory(
        query: String,
        onlyFavorites: Boolean,
        direction: String,
        threadId: Long,
    ): Flow<List<Analysis>> =
        db.analysisDao().search(query.trim(), onlyFavorites, direction, threadId)
            .map { list -> list.map { it.toDomain() } }

    fun observeHistoryCount(): Flow<Int> = db.analysisDao().observeCount()

    suspend fun setFavorite(id: Long, favorite: Boolean) = db.analysisDao().setFavorite(id, favorite)

    suspend fun deleteAnalysis(id: Long) = db.analysisDao().delete(id)

    suspend fun clearHistory() {
        db.analysisDao().deleteAll()
    }

    suspend fun latestAnalysis(): Analysis? = db.analysisDao().latest()?.toDomain()

    suspend fun allAnalyses(): List<Analysis> =
        db.analysisDao().all().map { it.toDomain() }

    suspend fun allThreads(): List<MessageThread> =
        db.threadDao().all().map { it.toDomain() }

    suspend fun clearEverything() {
        db.analysisDao().deleteAll()
        db.vocabularyDao().deleteAll()
        db.grammarNoteDao().deleteAll()
        db.threadDao().deleteAll()
        settingsStore.setActiveThread(0L)
    }

    suspend fun analysisById(id: Long): Analysis? = db.analysisDao().byId(id)?.toDomain()

    // ---- model calls -----------------------------------------------------------

    suspend fun analyze(text: String, threadId: Long): Analysis {
        val message = text.trim()
        if (message.isEmpty()) throw AiException(AiFailure.EMPTY, "Empty message")

        val settings = settingsStore.settings.first()
        val key = keyStore.apiKey
        val context = contextBlock(settings, threadId)
        val hint = TurkishText.guessLanguage(message)

        val messages = listOf(
            ChatMessage("system", systemPrompt(settings)),
            ChatMessage("user", Prompts.analysisUserMessage(message, context, hint)),
        )
        val result = runWithRepair(messages, settings, key) { parser.parseAnalysis(it) }

        val detected = result.detectedLanguage
        val direction = when {
            detected.startsWith("tr") -> Direction.TR_EN
            detected.startsWith("en") -> Direction.EN_TR
            hint == "tr" -> Direction.TR_EN
            else -> Direction.EN_TR
        }
        val entity = result.toEntity(
            threadId = threadId,
            sourceText = message,
            direction = direction,
            createdAt = System.currentTimeMillis(),
        )
        return persist(entity, result, threadId)
    }

    suspend fun composeReply(english: String, style: ReplyStyle, threadId: Long): Analysis {
        val text = english.trim()
        if (text.isEmpty()) throw AiException(AiFailure.EMPTY, "Empty message")

        val settings = settingsStore.settings.first()
        val key = keyStore.apiKey
        val context = contextBlock(settings, threadId)

        val messages = listOf(
            ChatMessage("system", Prompts.REPLY_SYSTEM),
            ChatMessage(
                "user",
                Prompts.replyUserMessage(text, style.label, style.instruction, context),
            ),
        )
        val result = runWithRepair(messages, settings, key) { parser.parseReply(it) }
        val entity = result.toEntity(
            threadId = threadId,
            sourceText = text,
            styleId = style.id,
            createdAt = System.currentTimeMillis(),
        )
        val saved = db.analysisDao().insert(entity)
        db.threadDao().touch(threadId, System.currentTimeMillis())
        autoTitleThread(threadId, text)
        return entity.copy(id = saved).toDomain()
    }

    suspend fun testConnection(): String {
        val settings = settingsStore.settings.first()
        if (settings.presetId == ProviderPresets.device.id) {
            val file = deviceModelFile(settings)
            localEngine.preload(file, offload = settings.gpuOffload)
            return localEngine.complete(
                file = file,
                messages = listOf(
                    ChatMessage("system", Prompts.TEST_SYSTEM),
                    ChatMessage("user", "ping"),
                ),
                temperature = 0f,
                maxTokens = 48,
                offload = settings.gpuOffload,
            ).trim().take(120)
        }
        val key = keyStore.apiKey
        if (key.isBlank()) throw AiException(AiFailure.NO_KEY, "Missing API key")
        if (settings.baseUrl.isBlank() || settings.model.isBlank()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "Missing base URL or model")
        }
        return ai.testConnection(settings.baseUrl, settings.model, key)
    }

    /** The device prompt is a compressed version of the same contract. */
    private fun systemPrompt(settings: AppSettings): String =
        if (settings.presetId == ProviderPresets.device.id) {
            Prompts.ANALYSIS_SYSTEM_DEVICE
        } else {
            Prompts.ANALYSIS_SYSTEM
        }

    /** One call, whichever brain is selected. */
    private suspend fun complete(
        messages: List<ChatMessage>,
        settings: AppSettings,
        key: String,
        temperature: Float,
    ): String {
        if (settings.presetId == ProviderPresets.device.id) {
            return localEngine.complete(
                file = deviceModelFile(settings),
                messages = messages,
                temperature = temperature,
                maxTokens = DEVICE_MAX_TOKENS,
                offload = settings.gpuOffload,
            )
        }
        return ai.complete(
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = key,
            messages = messages,
            temperature = temperature,
        )
    }

    private fun deviceModelFile(settings: AppSettings): java.io.File {
        val name = settings.deviceModel
        if (name.isBlank()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "Choose an on-device model in Settings")
        }
        val file = modelStore.file(name)
        if (!file.exists()) {
            throw AiException(AiFailure.NOT_CONFIGURED, "The on-device model file is missing")
        }
        return file
    }

    /** One automatic JSON-repair round; never fabricates a result. */
    private suspend fun <T> runWithRepair(
        messages: List<ChatMessage>,
        settings: AppSettings,
        key: String,
        parse: (String) -> T,
    ): T {
        val first = complete(messages, settings, key, settings.temperature)
        return try {
            parse(first)
        } catch (e: AiException) {
            if (e.failure != AiFailure.MALFORMED) throw e
            if (e.message?.contains("no translation", ignoreCase = true) == true) throw e
            val repaired = complete(
                messages = messages + listOf(
                    ChatMessage("assistant", first.take(4000)),
                    ChatMessage("user", Prompts.REPAIR),
                ),
                settings = settings,
                key = key,
                temperature = 0.1f,
            )
            parse(repaired)
        }
    }

    private suspend fun persist(
        entity: AnalysisEntity,
        result: AnalysisResult,
        threadId: Long,
    ): Analysis {
        val id = db.analysisDao().insert(entity)
        db.threadDao().touch(threadId, System.currentTimeMillis())
        autoTitleThread(threadId, entity.sourceText)
        captureGrammar(result, entity.sourceText)
        return entity.copy(id = id).toDomain()
    }

    private suspend fun captureGrammar(result: AnalysisResult, sourceText: String) {
        val notes = result.grammarNotes.trim()
        if (notes.length < 24) return
        val title = notes.substringBefore('.').substringBefore(';').trim().take(64)
        if (title.length < 8) return
        db.grammarNoteDao().insert(
            GrammarNoteEntity(
                title = title,
                explanation = notes,
                exampleTr = result.breakdown.joinToString(" ") { it.turkish }.take(180),
                exampleEn = result.breakdown.joinToString(" ") { it.meaning }.take(180),
                sourceText = sourceText.take(180),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun autoTitleThread(threadId: Long, text: String) {
        val thread = db.threadDao().byId(threadId) ?: return
        val autoNames = setOf("New conversation", "General Turkish", "")
        if (thread.title !in autoNames) return
        val title = text.trim().replace(Regex("\\s+"), " ").take(38)
        if (title.isNotBlank()) db.threadDao().rename(threadId, title, System.currentTimeMillis())
    }

    private suspend fun contextBlock(settings: AppSettings, threadId: Long): String {
        if (!settings.contextEnabled || settings.contextLimit <= 0) return ""
        val limit = settings.contextLimit
        val messages = db.analysisDao().contextMessages(threadId, limit).reversed()
        if (messages.isEmpty()) return ""
        val block = messages.joinToString("\n") { item ->
            val direction = if (item.direction == Direction.TR_EN.wire) "Turkish message" else "English message"
            val gloss = if (item.direction == Direction.TR_EN.wire) "understood as" else "sent as"
            "- $direction: ${item.sourceText.replace(Regex("\\s+"), " ").take(220)}\n  $gloss: ${item.naturalTranslation.replace(Regex("\\s+"), " ").take(240)}"
        }
        return block.take(3600)
    }
}

/** Smaller than the remote budget: a 3B model on a phone must stay responsive. */
private const val DEVICE_MAX_TOKENS = 900
