package com.euptdicio.importer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.GZIPInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

fun main(args: Array<String>) {
    val config = ImportConfig.fromArgs(args)
    KaikkiImporter(config).run()
}

data class ImportConfig(
    val input: Path,
    val output: Path,
    val sourceUrl: String,
    val limit: Int?,
) {
    companion object {
        fun fromArgs(args: Array<String>): ImportConfig {
            val values = args.asList().chunked(2).associate { chunk ->
                require(chunk.size == 2 && chunk[0].startsWith("--")) {
                    "Arguments must be --input <file> --output <db> [--source-url <url>] [--limit <count>]"
                }
                chunk[0].removePrefix("--") to chunk[1]
            }
            val input = values["input"]?.let(Path::of)
                ?: error("Missing --input")
            val output = values["output"]?.let(Path::of)
                ?: error("Missing --output")
            return ImportConfig(
                input = input,
                output = output,
                sourceUrl = values["source-url"] ?: "https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl",
                limit = values["limit"]?.toInt(),
            )
        }
    }
}

class KaikkiImporter(private val config: ImportConfig) {
    private val mapper = ObjectMapper()

    fun run() {
        require(config.input.exists()) { "Input does not exist: ${config.input.absolutePathString()}" }
        config.output.parent?.createDirectories()
        config.output.deleteIfExists()

        DriverManager.getConnection("jdbc:sqlite:${config.output.absolutePathString()}").use { connection ->
            configureConnection(connection)
            createSchema(connection)
            importJsonl(connection)
            optimize(connection)
        }
    }

    private fun configureConnection(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("PRAGMA journal_mode = OFF")
            statement.executeUpdate("PRAGMA synchronous = OFF")
            statement.executeUpdate("PRAGMA temp_store = MEMORY")
        }
        connection.autoCommit = false
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE source_manifest (
                    source_id TEXT PRIMARY KEY,
                    source_url TEXT NOT NULL,
                    license TEXT NOT NULL,
                    extracted_from TEXT NOT NULL,
                    imported_at_utc TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE entries (
                    entry_id INTEGER PRIMARY KEY,
                    lemma TEXT NOT NULL,
                    pos TEXT NOT NULL,
                    source_id TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE senses (
                    sense_id INTEGER PRIMARY KEY,
                    entry_id INTEGER NOT NULL,
                    gloss TEXT NOT NULL,
                    FOREIGN KEY(entry_id) REFERENCES entries(entry_id)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE forms (
                    entry_id INTEGER NOT NULL,
                    form TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    FOREIGN KEY(entry_id) REFERENCES entries(entry_id)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE VIRTUAL TABLE search_fts USING fts5(
                    lemma,
                    meanings,
                    forms,
                    tokenize = 'unicode61 remove_diacritics 2'
                )
                """.trimIndent(),
            )
        }

        connection.prepareStatement(
            """
            INSERT INTO source_manifest(source_id, source_url, license, extracted_from, imported_at_utc)
            VALUES (?, ?, ?, ?, datetime('now'))
            """.trimIndent(),
        ).use { insert ->
            insert.setString(1, SOURCE_ID)
            insert.setString(2, config.sourceUrl)
            insert.setString(3, "CC BY-SA 3.0 / GFDL inherited from Wiktionary content")
            insert.setString(4, "English Wiktionary via Wiktextract / Kaikki")
            insert.executeUpdate()
        }
        connection.commit()
    }

    private fun importJsonl(connection: Connection) {
        val insertEntry = connection.prepareStatement(
            "INSERT INTO entries(lemma, pos, source_id) VALUES (?, ?, ?)",
            arrayOf("entry_id"),
        )
        val insertSense = connection.prepareStatement(
            "INSERT INTO senses(entry_id, gloss) VALUES (?, ?)",
        )
        val insertForm = connection.prepareStatement(
            "INSERT INTO forms(entry_id, form, tags) VALUES (?, ?, ?)",
        )
        val insertSearch = connection.prepareStatement(
            "INSERT INTO search_fts(rowid, lemma, meanings, forms) VALUES (?, ?, ?, ?)",
        )

        var seen = 0
        var imported = 0
        openReader(config.input).useLines { lines ->
            for (line in lines) {
                if (config.limit != null && seen >= config.limit) break
                seen += 1

                val node = mapper.readTree(line)
                val record = node.toDictionaryRecord() ?: continue
                imported += 1

                insertEntry.setString(1, record.lemma)
                insertEntry.setString(2, record.pos)
                insertEntry.setString(3, SOURCE_ID)
                insertEntry.executeUpdate()

                val entryId = insertEntry.generatedKeys.use { keys ->
                    check(keys.next()) { "Missing generated entry id" }
                    keys.getLong(1)
                }

                record.glosses.forEach { gloss ->
                    insertSense.setLong(1, entryId)
                    insertSense.setString(2, gloss)
                    insertSense.addBatch()
                }
                record.forms.forEach { form ->
                    insertForm.setLong(1, entryId)
                    insertForm.setString(2, form.text)
                    insertForm.setString(3, form.tags.joinToString(","))
                    insertForm.addBatch()
                }
                insertSearch.setLong(1, entryId)
                insertSearch.setString(2, record.lemma)
                insertSearch.setString(3, record.glosses.joinToString(" | "))
                insertSearch.setString(4, record.forms.joinToString(" ") { it.text })
                insertSearch.addBatch()

                if (imported % BATCH_SIZE == 0) {
                    insertSense.executeBatch()
                    insertForm.executeBatch()
                    insertSearch.executeBatch()
                    connection.commit()
                    println("Imported $imported entries")
                }
            }
        }

        insertSense.executeBatch()
        insertForm.executeBatch()
        insertSearch.executeBatch()
        connection.commit()
        println("Imported $imported entries from $seen JSONL records into ${config.output.absolutePathString()}")
    }

    private fun optimize(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE INDEX idx_entries_lemma ON entries(lemma)")
            statement.executeUpdate("CREATE INDEX idx_forms_form ON forms(form)")
            statement.executeUpdate("CREATE INDEX idx_senses_entry ON senses(entry_id)")
            statement.executeUpdate("INSERT INTO search_fts(search_fts) VALUES('optimize')")
        }
        connection.commit()
    }

    private fun JsonNode.toDictionaryRecord(): DictionaryRecord? {
        if (path("lang_code").asText() != "pt") return null
        val lemma = path("word").asText("").trim()
        val pos = path("pos").asText("").trim()
        if (lemma.isBlank() || pos.isBlank()) return null

        val glosses = path("senses")
            .filter(JsonNode::isObject)
            .flatMap { sense -> sense.path("glosses").map(JsonNode::asText) }
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
        if (glosses.isEmpty()) return null

        val forms = path("forms")
            .filter(JsonNode::isObject)
            .mapNotNull { form ->
                val text = form.path("form").asText("").trim()
                if (text.isBlank() || text == lemma) {
                    null
                } else {
                    WordForm(
                        text = text,
                        tags = form.path("tags").map(JsonNode::asText).filter(String::isNotBlank),
                    )
                }
            }
            .distinctBy { it.text }

        return DictionaryRecord(
            lemma = lemma,
            pos = pos,
            glosses = glosses,
            forms = forms,
        )
    }

    private fun openReader(path: Path): BufferedReader {
        val stream = FileInputStream(path.toFile()).let { input ->
            if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(input) else input
        }
        return stream.bufferedReader(StandardCharsets.UTF_8)
    }

    private data class DictionaryRecord(
        val lemma: String,
        val pos: String,
        val glosses: List<String>,
        val forms: List<WordForm>,
    )

    private data class WordForm(
        val text: String,
        val tags: List<String>,
    )

    companion object {
        private const val BATCH_SIZE = 5_000
        private const val SOURCE_ID = "kaikki-portuguese"
    }
}
