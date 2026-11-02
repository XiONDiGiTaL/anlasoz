package net.xiondigital.anlasoz.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.GrammarNote
import net.xiondigital.anlasoz.data.model.MessageThread
import net.xiondigital.anlasoz.data.model.VocabularyEntry
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes learning material to the app cache and hands it to the share sheet. */
class ExportManager(private val context: Context) {

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        .withZone(ZoneId.systemDefault())

    suspend fun exportHistory(
        threads: List<MessageThread>,
        analyses: List<Analysis>,
        vocabulary: List<VocabularyEntry>,
        grammar: List<GrammarNote>,
    ): Uri {
        val payload = buildJsonObject {
            put("app", "AnlaSöz")
            put("exported_at", Instant.now().toString())
            put(
                "threads",
                buildJsonArray {
                    threads.forEach { thread ->
                        add(
                            buildJsonObject {
                                put("id", thread.id)
                                put("title", thread.title)
                                put("created_at", Instant.ofEpochMilli(thread.createdAt).toString())
                            },
                        )
                    }
                },
            )
            put(
                "exchanges",
                buildJsonArray {
                    analyses.forEach { item ->
                        add(
                            buildJsonObject {
                                put("thread_id", item.threadId)
                                put("direction", item.direction.wire)
                                put("created_at", Instant.ofEpochMilli(item.createdAt).toString())
                                put("source_text", item.sourceText)
                                put("natural_translation", item.naturalTranslation)
                                put("literal_translation", item.literalTranslation)
                                put("contextual_interpretation", item.contextual)
                                put("tone", item.tones.joinToString(", "))
                                put("tone_note", item.toneNote)
                                put("grammar_notes", item.grammarNotes)
                                put(
                                    "breakdown",
                                    buildJsonArray {
                                        item.breakdown.forEach { part ->
                                            add(
                                                buildJsonObject {
                                                    put("turkish", part.turkish)
                                                    put("meaning", part.meaning)
                                                    put("note", part.note)
                                                },
                                            )
                                        }
                                    },
                                )
                                put(
                                    "vocabulary",
                                    buildJsonArray {
                                        item.vocabulary.forEach { word ->
                                            add(
                                                buildJsonObject {
                                                    put("turkish", word.turkish)
                                                    put("english", word.english)
                                                    put("pronunciation", word.pronunciation)
                                                    put("example", word.example)
                                                    put("note", word.note)
                                                },
                                            )
                                        }
                                    },
                                )
                                put("style", item.style)
                                put("favorite", item.favorite)
                            },
                        )
                    }
                },
            )
            put(
                "vocabulary",
                buildJsonArray {
                    vocabulary.forEach { word ->
                        add(
                            buildJsonObject {
                                put("turkish", word.turkish)
                                put("english", word.english)
                                put("pronunciation", word.pronunciation)
                                put("example", word.example)
                                put("context", word.context)
                                put("grammar_note", word.grammarNote)
                                put("reviews", word.reviews)
                                put("starred", word.starred)
                            },
                        )
                    }
                },
            )
            put(
                "grammar_notes",
                buildJsonArray {
                    grammar.forEach { note ->
                        add(
                            buildJsonObject {
                                put("title", note.title)
                                put("explanation", note.explanation)
                                put("example_tr", note.exampleTr)
                                put("example_en", note.exampleEn)
                                put("source_text", note.sourceText)
                            },
                        )
                    }
                },
            )
        }
        val file = File(exportsDir(), "anlasoz-history-${stamp.format(Instant.now())}.json")
        file.writeText(payload.toString())
        return uriFor(file)
    }

    suspend fun exportVocabularyCsv(vocabulary: List<VocabularyEntry>): Uri {
        val header = listOf(
            "turkish", "english", "pronunciation", "example", "context", "grammar_note", "reviews", "starred",
        ).joinToString(",")
        val rows = vocabulary.map { word ->
            listOf(
                word.turkish,
                word.english,
                word.pronunciation,
                word.example,
                word.context,
                word.grammarNote,
                word.reviews.toString(),
                if (word.starred) "yes" else "no",
            ).joinToString(",") { csv(it) }
        }
        val file = File(exportsDir(), "anlasoz-vocabulary-${stamp.format(Instant.now())}.csv")
        // UTF-8 BOM keeps Turkish characters intact in spreadsheet apps.
        file.writeText("\uFEFF" + (listOf(header) + rows).joinToString("\r\n"))
        return uriFor(file)
    }

    fun share(uri: Uri, mime: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun exportsDir(): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
