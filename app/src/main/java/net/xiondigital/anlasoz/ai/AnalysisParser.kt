package net.xiondigital.anlasoz.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import net.xiondigital.anlasoz.data.model.AnalysisResult
import net.xiondigital.anlasoz.data.model.BreakdownItem
import net.xiondigital.anlasoz.data.model.ReplyResult
import net.xiondigital.anlasoz.data.model.ReplySuggestion
import net.xiondigital.anlasoz.data.model.VocabItem

/**
 * Tolerant parsing of model output.
 *
 * Even with JSON mode, providers sometimes wrap the object in fences, rename a
 * key, or use a string where the schema asked for a list. This parser accepts
 * those variations instead of failing the whole analysis, and reports
 * [AiFailure.MALFORMED] only when no object can be recovered at all.
 */
class AnalysisParser(private val json: Json = Json { isLenient = true; ignoreUnknownKeys = true }) {

    fun parseAnalysis(raw: String): AnalysisResult {
        val obj = recoverObject(raw)
        return AnalysisResult(
            detectedLanguage = obj.str("detected_language", "language", "lang").lowercase(),
            naturalTranslation = obj.str("natural_translation", "translation", "english_translation"),
            literalTranslation = obj.str("literal_translation", "literal"),
            contextual = obj.str("contextual_interpretation", "context", "interpretation"),
            tones = obj.strList("tone", "tones"),
            toneNote = obj.str("tone_note", "tone_explanation"),
            breakdown = obj.objects("sentence_breakdown", "breakdown").map {
                BreakdownItem(
                    turkish = it.str("turkish", "word", "token", "text"),
                    meaning = it.str("meaning", "english", "translation"),
                    note = it.str("note", "grammar", "explanation"),
                )
            }.filter { it.turkish.isNotBlank() || it.meaning.isNotBlank() },
            grammarNotes = obj.str("grammar_notes", "grammar"),
            vocabulary = obj.objects("vocabulary", "vocab", "words").map {
                VocabItem(
                    turkish = it.str("turkish", "word", "expression"),
                    english = it.str("english", "meaning", "translation"),
                    pronunciation = it.str("pronunciation", "syllables"),
                    example = it.str("example", "example_sentence"),
                    note = it.str("note", "usage", "context", "register"),
                )
            }.filter { it.turkish.isNotBlank() },
            alternatives = obj.strList("alternative_interpretations", "alternatives", "other_meanings"),
            suggestedReplies = obj.objects("suggested_replies", "replies", "suggestions").map {
                ReplySuggestion(
                    turkish = it.str("turkish", "reply", "text"),
                    english = it.str("english", "meaning", "translation"),
                    tone = it.str("tone"),
                )
            }.filter { it.turkish.isNotBlank() },
            confidence = obj.str("confidence_notes", "confidence", "uncertainty"),
        ).validated()
    }

    fun parseReply(raw: String): ReplyResult {
        val obj = recoverObject(raw)
        return ReplyResult(
            naturalTurkish = obj.str("natural_turkish", "turkish", "reply", "message"),
            backTranslation = obj.str("back_translation", "english", "meaning"),
            tone = obj.str("tone"),
            toneNote = obj.str("tone_note", "tone_explanation"),
            alternatives = obj.objects("alternatives", "alternative_phrasings", "variants").map {
                ReplySuggestion(
                    turkish = it.str("turkish", "text", "reply"),
                    english = it.str("english", "meaning", "back_translation"),
                    tone = it.str("nuance", "note", "tone"),
                )
            }.filter { it.turkish.isNotBlank() },
            usageNote = obj.str("usage_note", "note", "register_note"),
        ).validated()
    }

    /** Finds and parses the first JSON object in the reply, ignoring fences and prose. */
    fun recoverObject(raw: String): JsonObject {
        val cleaned = raw
            .replace(Regex("^```(?:json)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```$"), "")
            .trim()
        runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()?.let { return it }

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val candidate = cleaned.substring(start, end + 1)
            runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let { return it }
            // Trailing commas are a common model slip.
            val fixed = candidate.replace(Regex(",\\s*([}\\]])"), "$1")
            runCatching { json.parseToJsonElement(fixed).jsonObject }.getOrNull()?.let { return it }
        }
        throw AiException(AiFailure.MALFORMED, "No JSON object found in the model reply")
    }

    // ---- lenient field readers -------------------------------------------------

    private fun JsonObject.raw(vararg keys: String): JsonElement? {
        for (key in keys) this[key]?.let { return it }
        return null
    }

    fun JsonObject.str(vararg keys: String): String {
        val element = raw(*keys) ?: return ""
        return when (element) {
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> element.joinToString("; ") { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull.orEmpty()
                    is JsonObject -> part.str("text", "turkish", "meaning", "english")
                    else -> ""
                }
            }.trim().trim(';', ' ')
            is JsonObject -> element.str("text", "turkish", "english", "meaning", "value")
            else -> ""
        }.cleanText()
    }

    private fun JsonObject.strList(vararg keys: String): List<String> {
        val element = raw(*keys) ?: return emptyList()
        val list = when (element) {
            is JsonArray -> element.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> part.str("tone", "label", "text", "value").ifBlank { null }
                    else -> null
                }
            }
            is JsonPrimitive -> element.contentOrNull
                ?.split(',', '/', ';')
                ?.map { it.trim() }
                .orEmpty()
            is JsonObject -> listOfNotNull(element.str("tone", "label", "value").ifBlank { null })
            else -> emptyList()
        }
        return list.map { it.cleanText() }.filter { it.isNotBlank() }.distinct().take(6)
    }

    private fun JsonObject.objects(vararg keys: String): List<JsonObject> {
        val element = raw(*keys) ?: return emptyList()
        return when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
    }
}

private fun String.cleanText(): String = this
    .replace(Regex("[*_`#]+"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun AnalysisResult.validated(): AnalysisResult {
    if (naturalTranslation.isBlank()) {
        throw AiException(AiFailure.MALFORMED, "The model reply had no translation")
    }
    return this
}

private fun ReplyResult.validated(): ReplyResult {
    if (naturalTurkish.isBlank()) {
        throw AiException(AiFailure.MALFORMED, "The model reply had no Turkish text")
    }
    return this
}
