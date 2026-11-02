package net.xiondigital.anlasoz.data.model

import kotlinx.serialization.Serializable

enum class Direction(val wire: String) {
    TR_EN("TR_EN"),
    EN_TR("EN_TR");

    companion object {
        fun from(wire: String): Direction =
            entries.firstOrNull { it.wire == wire } ?: TR_EN
    }
}

@Serializable
data class BreakdownItem(
    val turkish: String = "",
    val meaning: String = "",
    val note: String = "",
)

@Serializable
data class VocabItem(
    val turkish: String = "",
    val english: String = "",
    val pronunciation: String = "",
    val example: String = "",
    val note: String = "",
)

@Serializable
data class ReplySuggestion(
    val turkish: String = "",
    val english: String = "",
    val tone: String = "",
)

/** Everything the model returns for one analyzed message. */
@Serializable
data class AnalysisResult(
    val detectedLanguage: String = "",
    val naturalTranslation: String = "",
    val literalTranslation: String = "",
    val contextual: String = "",
    val tones: List<String> = emptyList(),
    val toneNote: String = "",
    val breakdown: List<BreakdownItem> = emptyList(),
    val grammarNotes: String = "",
    val vocabulary: List<VocabItem> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val suggestedReplies: List<ReplySuggestion> = emptyList(),
    val confidence: String = "",
)

/** Everything the model returns for one generated Turkish reply. */
@Serializable
data class ReplyResult(
    val naturalTurkish: String = "",
    val backTranslation: String = "",
    val tone: String = "",
    val toneNote: String = "",
    val alternatives: List<ReplySuggestion> = emptyList(),
    val usageNote: String = "",
)

/** A persisted exchange: either an analysis or a generated reply. */
data class Analysis(
    val id: Long,
    val threadId: Long,
    val direction: Direction,
    val sourceText: String,
    val naturalTranslation: String,
    val literalTranslation: String = "",
    val contextual: String = "",
    val tones: List<String> = emptyList(),
    val toneNote: String = "",
    val breakdown: List<BreakdownItem> = emptyList(),
    val grammarNotes: String = "",
    val vocabulary: List<VocabItem> = emptyList(),
    /** Alternative readings of the message. */
    val interpretations: List<String> = emptyList(),
    /** Turkish phrasings the learner could send back. */
    val replies: List<ReplySuggestion> = emptyList(),
    val confidence: String = "",
    val style: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
)

data class MessageThread(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class VocabularyEntry(
    val id: Long,
    val turkish: String,
    val english: String,
    val pronunciation: String = "",
    val example: String = "",
    val context: String = "",
    val grammarNote: String = "",
    val createdAt: Long = 0L,
    val reviews: Int = 0,
    val starred: Boolean = false,
)

data class GrammarNote(
    val id: Long,
    val title: String,
    val explanation: String,
    val exampleTr: String = "",
    val exampleEn: String = "",
    val sourceText: String = "",
    val createdAt: Long = 0L,
)

/** Reply styles offered in the Reply tab; `natural` is the default. */
enum class ReplyStyle(val id: String, val label: String, val instruction: String) {
    NATURAL("natural", "Natural", "Everyday conversational Turkish, the way a native speaker texts."),
    AFFECTIONATE("affectionate", "Affectionate", "Warm and tender, using natural Turkish endearments only where the meaning supports it."),
    ROMANTIC("romantic", "Romantic", "Clearly romantic and intimate, without becoming florid or poetic."),
    PLAYFUL("playful", "Playful", "Light teasing and humour, the way friends joke in Turkish."),
    FLIRTATIOUS("flirtatious", "Flirtatious", "Subtly flirtatious and confident, never explicit."),
    FORMAL("formal", "Formal", "Polite, respectful Turkish using siz and full verb forms."),
    CASUAL("casual", "Casual", "Very short informal texting Turkish, abbreviations and lowercase where natural."),
    DIRECT("direct", "Direct", "Plain, unhedged Turkish that says exactly what is meant."),
    REASSURING("reassuring", "Reassuring", "Calm, comforting Turkish that settles the other person."),
    HUMOROUS("humorous", "Humorous", "A light joke or witty line that still carries the original meaning.");

    companion object {
        fun from(id: String?): ReplyStyle = entries.firstOrNull { it.id == id } ?: NATURAL
    }
}
