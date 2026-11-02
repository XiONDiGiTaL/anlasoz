package net.xiondigital.anlasoz.data.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.AnalysisResult
import net.xiondigital.anlasoz.data.model.BreakdownItem
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.data.model.GrammarNote
import net.xiondigital.anlasoz.data.model.MessageThread
import net.xiondigital.anlasoz.data.model.ReplyResult
import net.xiondigital.anlasoz.data.model.ReplySuggestion
import net.xiondigital.anlasoz.data.model.VocabItem
import net.xiondigital.anlasoz.data.model.VocabularyEntry

internal val storeJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

private inline fun <reified T> decodeList(raw: String, fallback: List<T> = emptyList()): List<T> =
    if (raw.isBlank()) fallback else runCatching { storeJson.decodeFromString<List<T>>(raw) }.getOrElse { fallback }

fun AnalysisEntity.toDomain(): Analysis = Analysis(
    id = id,
    threadId = threadId,
    direction = Direction.from(direction),
    sourceText = sourceText,
    naturalTranslation = naturalTranslation,
    literalTranslation = literalTranslation,
    contextual = contextual,
    tones = tone.split("|").map { it.trim() }.filter { it.isNotEmpty() },
    toneNote = toneNote,
    breakdown = decodeList<BreakdownItem>(breakdownJson),
    grammarNotes = grammarNotes,
    vocabulary = decodeList<VocabItem>(vocabularyJson),
    interpretations = decodeList<String>(interpretationsJson),
    replies = decodeList<ReplySuggestion>(repliesJson),
    confidence = confidence,
    style = style,
    favorite = favorite,
    createdAt = createdAt,
)

fun AnalysisResult.toEntity(
    threadId: Long,
    sourceText: String,
    direction: Direction,
    createdAt: Long,
): AnalysisEntity = AnalysisEntity(
    threadId = threadId,
    direction = direction.wire,
    sourceText = sourceText,
    naturalTranslation = naturalTranslation,
    literalTranslation = literalTranslation,
    contextual = contextual,
    tone = tones.joinToString("|"),
    toneNote = toneNote,
    breakdownJson = storeJson.encodeToString(breakdown),
    grammarNotes = grammarNotes,
    vocabularyJson = storeJson.encodeToString(vocabulary),
    interpretationsJson = storeJson.encodeToString(alternatives),
    repliesJson = storeJson.encodeToString(suggestedReplies),
    confidence = confidence,
    createdAt = createdAt,
)

fun ReplyResult.toEntity(
    threadId: Long,
    sourceText: String,
    styleId: String,
    createdAt: Long,
): AnalysisEntity = AnalysisEntity(
    threadId = threadId,
    direction = Direction.EN_TR.wire,
    sourceText = sourceText,
    naturalTranslation = naturalTurkish,
    literalTranslation = backTranslation,
    contextual = "",
    tone = tone,
    toneNote = toneNote,
    breakdownJson = "[]",
    grammarNotes = "",
    vocabularyJson = "[]",
    interpretationsJson = "[]",
    repliesJson = storeJson.encodeToString(alternatives),
    confidence = usageNote,
    style = styleId,
    createdAt = createdAt,
)

fun ThreadEntity.toDomain(): MessageThread = MessageThread(id, title, createdAt, updatedAt)

fun VocabularyEntity.toDomain(): VocabularyEntry = VocabularyEntry(
    id = id,
    turkish = turkish,
    english = english,
    pronunciation = pronunciation,
    example = example,
    context = context,
    grammarNote = grammarNote,
    createdAt = createdAt,
    reviews = reviews,
    starred = starred,
)

fun GrammarNoteEntity.toDomain(): GrammarNote = GrammarNote(
    id = id,
    title = title,
    explanation = explanation,
    exampleTr = exampleTr,
    exampleEn = exampleEn,
    sourceText = sourceText,
    createdAt = createdAt,
)
