package net.xiondigital.anlasoz.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    suspend fun all(): List<ThreadEntity>

    @Query("SELECT * FROM threads WHERE id = :id")
    suspend fun byId(id: Long): ThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ThreadEntity): Long

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("UPDATE threads SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("UPDATE threads SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM threads")
    suspend fun deleteAll()
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeThread(threadId: Long): Flow<List<AnalysisEntity>>

    /** Thread messages produced by the analyze flow; reply drafts are excluded. */
    @Query("SELECT * FROM analyses WHERE threadId = :threadId AND style = '' ORDER BY createdAt ASC")
    fun observeThreadTranslations(threadId: Long): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AnalysisEntity>>

    @Query(
        """
        SELECT * FROM analyses
        WHERE (:query = '' OR sourceText LIKE '%' || :query || '%'
               OR naturalTranslation LIKE '%' || :query || '%')
          AND (:onlyFavorites = 0 OR favorite = 1)
          AND (:direction = '' OR direction = :direction)
          AND (:threadId = 0 OR threadId = :threadId)
        ORDER BY createdAt DESC
        """,
    )
    fun search(
        query: String,
        onlyFavorites: Boolean,
        direction: String,
        threadId: Long,
    ): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE threadId = :threadId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun contextMessages(threadId: Long, limit: Int): List<AnalysisEntity>

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun byId(id: Long): AnalysisEntity?

    @Query("SELECT * FROM analyses ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): AnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AnalysisEntity): Long

    @Query("UPDATE analyses SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM analyses WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM analyses")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM analyses ORDER BY createdAt ASC")
    suspend fun all(): List<AnalysisEntity>
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VocabularyEntity>>

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE turkish LIKE '%' || :query || '%' OR english LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """,
    )
    fun search(query: String): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY createdAt DESC")
    suspend fun all(): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary WHERE turkish = :turkish LIMIT 1")
    suspend fun byTurkish(turkish: String): VocabularyEntity?

    @Query("SELECT turkish FROM vocabulary")
    fun observeTurkishWords(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: VocabularyEntity): Long

    @Update
    suspend fun update(item: VocabularyEntity)

    @Query("UPDATE vocabulary SET reviews = reviews + 1, lastReviewedAt = :now WHERE id = :id")
    suspend fun markReviewed(id: Long, now: Long)

    @Query("UPDATE vocabulary SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vocabulary")
    suspend fun deleteAll()
}

@Dao
interface GrammarNoteDao {
    @Query("SELECT * FROM grammar_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GrammarNoteEntity>>

    @Query("SELECT * FROM grammar_notes ORDER BY createdAt DESC")
    suspend fun all(): List<GrammarNoteEntity>

    @Query("SELECT * FROM grammar_notes WHERE title = :title LIMIT 1")
    suspend fun byTitle(title: String): GrammarNoteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(note: GrammarNoteEntity): Long

    @Delete
    suspend fun delete(note: GrammarNoteEntity)

    @Query("DELETE FROM grammar_notes")
    suspend fun deleteAll()
}
