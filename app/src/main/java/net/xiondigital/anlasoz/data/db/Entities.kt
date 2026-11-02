package net.xiondigital.anlasoz.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "analyses",
    indices = [Index("threadId"), Index("createdAt"), Index("favorite")],
)
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val direction: String,
    val sourceText: String,
    val naturalTranslation: String,
    val literalTranslation: String = "",
    val contextual: String = "",
    val tone: String = "",
    val toneNote: String = "",
    val breakdownJson: String = "[]",
    val grammarNotes: String = "",
    val vocabularyJson: String = "[]",
    val interpretationsJson: String = "[]",
    val repliesJson: String = "[]",
    val confidence: String = "",
    val style: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "vocabulary",
    indices = [Index(value = ["turkish"], unique = true), Index("createdAt")],
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turkish: String,
    val english: String,
    val pronunciation: String = "",
    val example: String = "",
    val context: String = "",
    val grammarNote: String = "",
    val createdAt: Long,
    val sourceAnalysisId: Long = 0,
    val reviews: Int = 0,
    val lastReviewedAt: Long = 0,
    val starred: Boolean = false,
)

@Entity(tableName = "grammar_notes", indices = [Index(value = ["title"], unique = true)])
data class GrammarNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val explanation: String,
    val exampleTr: String = "",
    val exampleEn: String = "",
    val sourceText: String = "",
    val createdAt: Long,
)
