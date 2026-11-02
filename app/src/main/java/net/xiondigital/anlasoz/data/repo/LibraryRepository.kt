package net.xiondigital.anlasoz.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.xiondigital.anlasoz.core.PracticeCard
import net.xiondigital.anlasoz.core.PracticeBuilder
import net.xiondigital.anlasoz.data.db.AppDatabase
import net.xiondigital.anlasoz.data.db.GrammarNoteEntity
import net.xiondigital.anlasoz.data.db.VocabularyEntity
import net.xiondigital.anlasoz.data.db.toDomain
import net.xiondigital.anlasoz.data.model.GrammarNote
import net.xiondigital.anlasoz.data.model.VocabItem
import net.xiondigital.anlasoz.data.model.VocabularyEntry

data class LibraryStats(
    val savedWords: Int = 0,
    val starredWords: Int = 0,
    val analyses: Int = 0,
    val grammarNotes: Int = 0,
    val reviews: Int = 0,
)

/** Saved vocabulary, captured grammar and the offline practice deck. */
class LibraryRepository(private val db: AppDatabase) {

    fun observeVocabulary(): Flow<List<VocabularyEntry>> =
        db.vocabularyDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun searchVocabulary(query: String): Flow<List<VocabularyEntry>> =
        db.vocabularyDao().search(query.trim()).map { list -> list.map { it.toDomain() } }

    fun observeGrammar(): Flow<List<GrammarNote>> =
        db.grammarNoteDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeStats(): Flow<LibraryStats> = combine(
        observeVocabulary(),
        db.analysisDao().observeCount(),
        observeGrammar(),
    ) { vocab, analyses, grammar ->
        LibraryStats(
            savedWords = vocab.size,
            starredWords = vocab.count { it.starred },
            analyses = analyses,
            grammarNotes = grammar.size,
            reviews = vocab.sumOf { it.reviews },
        )
    }

    suspend fun vocabularySnapshot(): List<VocabularyEntry> =
        db.vocabularyDao().all().map { it.toDomain() }

    suspend fun grammarSnapshot(): List<GrammarNote> =
        db.grammarNoteDao().all().map { it.toDomain() }

    /** Returns false when the word is already saved. */
    suspend fun saveVocabulary(item: VocabItem, analysisId: Long = 0): Boolean {
        val turkish = item.turkish.trim()
        if (turkish.isEmpty()) return false
        val id = db.vocabularyDao().insert(
            VocabularyEntity(
                turkish = turkish,
                english = item.english.trim(),
                pronunciation = item.pronunciation.trim(),
                example = item.example.trim(),
                context = item.note.trim(),
                grammarNote = "",
                createdAt = System.currentTimeMillis(),
                sourceAnalysisId = analysisId,
            ),
        )
        return id != -1L
    }

    suspend fun addVocabulary(
        turkish: String,
        english: String,
        pronunciation: String,
        example: String,
        context: String,
        grammarNote: String,
    ): Boolean {
        if (turkish.isBlank()) return false
        val id = db.vocabularyDao().insert(
            VocabularyEntity(
                turkish = turkish.trim(),
                english = english.trim(),
                pronunciation = pronunciation.trim(),
                example = example.trim(),
                context = context.trim(),
                grammarNote = grammarNote.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id != -1L
    }

    suspend fun updateVocabulary(entry: VocabularyEntry) {
        val existing = db.vocabularyDao().byTurkish(entry.turkish) ?: return
        db.vocabularyDao().update(
            existing.copy(
                turkish = entry.turkish,
                english = entry.english,
                pronunciation = entry.pronunciation,
                example = entry.example,
                context = entry.context,
                grammarNote = entry.grammarNote,
            ),
        )
    }

    suspend fun deleteVocabulary(id: Long) = db.vocabularyDao().delete(id)

    suspend fun setStarred(id: Long, starred: Boolean) = db.vocabularyDao().setStarred(id, starred)

    suspend fun markReviewed(id: Long) =
        db.vocabularyDao().markReviewed(id, System.currentTimeMillis())

    suspend fun saveGrammar(
        title: String,
        explanation: String,
        exampleTr: String = "",
        exampleEn: String = "",
        sourceText: String = "",
    ): Boolean {
        if (title.isBlank() || explanation.isBlank()) return false
        val id = db.grammarNoteDao().insert(
            GrammarNoteEntity(
                title = title.trim().take(80),
                explanation = explanation.trim(),
                exampleTr = exampleTr.trim(),
                exampleEn = exampleEn.trim(),
                sourceText = sourceText.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id != -1L
    }

    suspend fun deleteGrammar(note: GrammarNote) {
        db.grammarNoteDao().byTitle(note.title)?.let { db.grammarNoteDao().delete(it) }
    }

    suspend fun isVocabularySaved(turkish: String): Boolean =
        db.vocabularyDao().byTurkish(turkish.trim()) != null

    suspend fun practiceDeck(limit: Int = 14): List<PracticeCard> =
        PracticeBuilder.build(db.vocabularyDao().all().map { it.toDomain() }, limit)

    suspend fun clearLibrary() {
        db.vocabularyDao().deleteAll()
        db.grammarNoteDao().deleteAll()
    }
}
