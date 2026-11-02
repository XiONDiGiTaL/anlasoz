package net.xiondigital.anlasoz.core

import net.xiondigital.anlasoz.data.model.VocabularyEntry
import java.util.Locale

/**
 * Builds an offline review deck from the learner's own saved words.
 * No network, no generated filler: every card comes from something they saved.
 */
data class PracticeCard(
    val id: Long,
    val mode: Mode,
    val prompt: String,
    val answer: String,
    val detail: String,
    val example: String,
    val turkishWord: String,
) {
    enum class Mode { CLOZE, RECALL }
}

object PracticeBuilder {

    fun build(entries: List<VocabularyEntry>, limit: Int): List<PracticeCard> {
        if (entries.isEmpty()) return emptyList()
        // Least-reviewed words first so practice cycles through the whole library.
        val ordered = entries.sortedWith(
            compareBy<VocabularyEntry> { it.reviews }.thenByDescending { it.createdAt },
        )
        return ordered.take(limit).map { entry ->
            val cloze = clozeSentence(entry)
            if (cloze != null) {
                PracticeCard(
                    id = entry.id,
                    mode = PracticeCard.Mode.CLOZE,
                    prompt = cloze,
                    answer = entry.turkishWord(),
                    detail = entry.english,
                    example = entry.example,
                    turkishWord = entry.turkish,
                )
            } else {
                PracticeCard(
                    id = entry.id,
                    mode = PracticeCard.Mode.RECALL,
                    prompt = entry.english.ifBlank { "What does this mean?" },
                    answer = entry.turkish,
                    detail = entry.pronunciation,
                    example = entry.example.ifBlank { entry.context },
                    turkishWord = entry.turkish,
                )
            }
        }
    }

    /** Replaces the saved word inside its example sentence with a blank. */
    fun clozeSentence(entry: VocabularyEntry): String? {
        val example = entry.example.trim()
        if (example.isEmpty()) return null
        val word = entry.turkish.trim()
        if (word.isEmpty()) return null
        val lowerExample = example.lowercase(Locale("tr", "TR"))
        val lowerWord = word.lowercase(Locale("tr", "TR"))
        val at = lowerExample.indexOf(lowerWord)
        if (at < 0) return null
        val blanked = example.substring(0, at) + "_____" + example.substring(at + word.length)
        return "$blanked\n\n(${entry.english})"
    }

    private fun VocabularyEntry.turkishWord(): String = turkish
}
