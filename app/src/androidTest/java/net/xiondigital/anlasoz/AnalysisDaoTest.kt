package net.xiondigital.anlasoz

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.xiondigital.anlasoz.data.db.AnalysisEntity
import net.xiondigital.anlasoz.data.db.AppDatabase
import net.xiondigital.anlasoz.data.db.ThreadEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Translate screen restores the newest message of a conversation. Reply
 * drafts live in the same table, so the feed must filter them out or a generated
 * Turkish reply replaces the translation the user was reading.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun threadTranslationsExcludeReplyDrafts() = runBlocking {
        val threadId = db.threadDao().insert(ThreadEntity(title = "Conversation A", createdAt = 1L, updatedAt = 1L))
        db.analysisDao().insert(
            AnalysisEntity(
                threadId = threadId,
                direction = "TR_EN",
                sourceText = "Ben de seni özledim.",
                naturalTranslation = "I missed you too.",
                createdAt = 10L,
            ),
        )
        db.analysisDao().insert(
            AnalysisEntity(
                threadId = threadId,
                direction = "EN_TR",
                sourceText = "I miss you too. I wish I could be there.",
                naturalTranslation = "Ben de seni çok özledim.",
                style = "affectionate",
                createdAt = 20L,
            ),
        )

        val translations = db.analysisDao().observeThreadTranslations(threadId).first()
        val everything = db.analysisDao().observeThread(threadId).first()

        assertEquals(listOf("Ben de seni özledim."), translations.map { it.sourceText })
        assertEquals(2, everything.size)
    }
}
