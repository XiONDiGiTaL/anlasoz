package net.xiondigital.anlasoz

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.xiondigital.anlasoz.ai.AnalysisParser
import net.xiondigital.anlasoz.data.db.AnalysisEntity
import net.xiondigital.anlasoz.data.db.storeJson
import net.xiondigital.anlasoz.data.db.toDomain
import net.xiondigital.anlasoz.data.model.AnalysisResult
import net.xiondigital.anlasoz.data.model.BreakdownItem
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.data.model.ReplySuggestion
import net.xiondigital.anlasoz.data.model.VocabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperAndParserTest {

    private val analysisJson = """
        {
          "detected_language": "tr",
          "natural_translation": "I missed you too.",
          "literal_translation": "I too you missed.",
          "contextual_interpretation": "A warm reciprocal answer.",
          "tone": ["affectionate", "casual"],
          "tone_note": "Short and sincere.",
          "sentence_breakdown": [
            {"turkish": "Ben", "meaning": "I", "note": "pronoun"},
            {"turkish": "özledim", "meaning": "I missed", "note": "-di past, -m person"}
          ],
          "grammar_notes": "Verb-final clause with the object marked by -i.",
          "vocabulary": [
            {"turkish": "özlemek", "english": "to miss", "pronunciation": "öz-le-mek", "example": "Seni özledim.", "note": "everyday"}
          ],
          "alternative_interpretations": ["Could simply mean 'me too'."],
          "suggested_replies": [
            {"turkish": "Ben de seni özledim.", "english": "I missed you too.", "tone": "affectionate"}
          ],
          "confidence_notes": "Depends on the previous message."
        }
    """.trimIndent()

    @Test
    fun `parser reads the full schema`() {
        val result = AnalysisParser().parseAnalysis(analysisJson)
        assertEquals("tr", result.detectedLanguage)
        assertEquals("I missed you too.", result.naturalTranslation)
        assertEquals(listOf("affectionate", "casual"), result.tones)
        assertEquals(2, result.breakdown.size)
        assertEquals(1, result.vocabulary.size)
        assertEquals(1, result.alternatives.size)
        assertEquals("Ben de seni özledim.", result.suggestedReplies.first().turkish)
    }

    @Test
    fun `parser recovers json wrapped in prose and fences`() {
        val wrapped = "Here is the analysis:\n```json\n$analysisJson\n```\nHope this helps."
        val result = AnalysisParser().parseAnalysis(wrapped)
        assertEquals("I missed you too.", result.naturalTranslation)
    }

    @Test
    fun `parser survives a string where a list was requested`() {
        val json = buildJsonObject {
            put("detected_language", "tr")
            put("natural_translation", "Hello.")
            put("tone", "casual")
            put("suggested_replies", "tekrar dene")
        }.toString()
        val result = AnalysisParser().parseAnalysis(json)
        assertEquals(listOf("casual"), result.tones)
        assertEquals("Hello.", result.naturalTranslation)
    }

    /** The regression that hid every structured section in the UI. */
    @Test
    fun `entity mapping round-trips structured sections`() {
        val result = AnalysisParser().parseAnalysis(analysisJson)
        val entity = AnalysisEntity(
            threadId = 1,
            direction = Direction.TR_EN.wire,
            sourceText = "Ben de seni özledim.",
            naturalTranslation = result.naturalTranslation,
            literalTranslation = result.literalTranslation,
            contextual = result.contextual,
            tone = result.tones.joinToString("|"),
            toneNote = result.toneNote,
            breakdownJson = storeJson.encodeToString(List(2) { BreakdownItem("a", "b", "") }),
            grammarNotes = result.grammarNotes,
            vocabularyJson = storeJson.encodeToString(listOf(VocabItem("özlemek", "to miss"))),
            interpretationsJson = storeJson.encodeToString(result.alternatives),
            repliesJson = storeJson.encodeToString(listOf(ReplySuggestion("Ben de", "me too", "casual"))),
            confidence = result.confidence,
            createdAt = 0,
        )

        val domain = entity.toDomain()
        assertEquals(2, domain.breakdown.size)
        assertEquals(1, domain.vocabulary.size)
        assertEquals("özlemek", domain.vocabulary.first().turkish)
        assertEquals(1, domain.interpretations.size)
        assertEquals(1, domain.replies.size)
        assertEquals("Ben de", domain.replies.first().turkish)
        assertEquals(listOf("affectionate", "casual"), domain.tones)
    }

    @Test
    fun `stored lists survive malformed content without throwing`() {
        val entity = AnalysisEntity(
            threadId = 1,
            direction = Direction.TR_EN.wire,
            sourceText = "x",
            naturalTranslation = "y",
            breakdownJson = "{not json",
            vocabularyJson = "",
            createdAt = 0,
        )
        val domain = entity.toDomain()
        assertTrue(domain.breakdown.isEmpty())
        assertTrue(domain.vocabulary.isEmpty())
    }

    @Test
    fun `analysis result encodes with the same keys the parser reads`() {
        val parsed = AnalysisParser().parseAnalysis(analysisJson)
        val encoded = storeJson.encodeToString(AnalysisResult.serializer(), parsed)
        val reparsed = Json { ignoreUnknownKeys = true }.decodeFromString(AnalysisResult.serializer(), encoded)
        assertEquals(parsed, reparsed)
    }
}
