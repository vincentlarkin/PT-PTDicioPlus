package com.euptdicio.importer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.BufferedReader
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.xml.parsers.DocumentBuilderFactory
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
    val frequency: Path?,
    val freedictPortugueseEnglish: Path?,
    val freedictEnglishPortuguese: Path?,
    val limit: Int?,
) {
    companion object {
        fun fromArgs(args: Array<String>): ImportConfig {
            val values = args.asList().chunked(2).associate { chunk ->
                require(chunk.size == 2 && chunk[0].startsWith("--")) {
                    "Arguments must be --input <file> --output <db> [--source-url <url>] [--frequency <file>] [--freedict-por-eng <tei>] [--freedict-eng-por <tei>] [--limit <count>]"
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
                frequency = values["frequency"]?.let(Path::of),
                freedictPortugueseEnglish = values["freedict-por-eng"]?.let(Path::of),
                freedictEnglishPortuguese = values["freedict-eng-por"]?.let(Path::of),
                limit = values["limit"]?.toInt(),
            )
        }
    }
}

class KaikkiImporter(private val config: ImportConfig) {
    private val mapper = ObjectMapper()
    private val frequencySignals by lazy { loadFrequencySignals(config.frequency) }

    fun run() {
        require(config.input.exists()) { "Input does not exist: ${config.input.absolutePathString()}" }
        config.output.parent?.createDirectories()
        config.output.deleteIfExists()

        DriverManager.getConnection("jdbc:sqlite:${config.output.absolutePathString()}").use { connection ->
            configureConnection(connection)
            createSchema(connection)
            importSources(connection)
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
                    source_id TEXT NOT NULL,
                    commonality_score INTEGER NOT NULL DEFAULT 0,
                    frequency_rank INTEGER,
                    frequency INTEGER,
                    frequency_source_id TEXT
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE senses (
                    sense_id INTEGER PRIMARY KEY,
                    entry_id INTEGER NOT NULL,
                    gloss TEXT NOT NULL,
                    gloss_lc TEXT NOT NULL,
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
                CREATE TABLE examples (
                    example_id INTEGER PRIMARY KEY,
                    entry_id INTEGER NOT NULL,
                    sentence TEXT NOT NULL,
                    translation TEXT,
                    source_id TEXT NOT NULL,
                    FOREIGN KEY(entry_id) REFERENCES entries(entry_id)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE VIRTUAL TABLE search_fts USING fts4(
                    lemma,
                    meanings,
                    forms,
                    tokenize=unicode61 "remove_diacritics=2"
                )
                """.trimIndent(),
            )
        }

        insertSourceManifest(
            connection = connection,
            sourceId = KAIKKI_SOURCE_ID,
            sourceUrl = config.sourceUrl,
            license = "CC BY-SA 3.0 / GFDL inherited from Wiktionary content",
            extractedFrom = "English Wiktionary via Wiktextract / Kaikki",
        )
        if (config.freedictPortugueseEnglish != null) {
            insertSourceManifest(
                connection = connection,
                sourceId = FREEDICT_PT_EN_SOURCE_ID,
                sourceUrl = FREEDICT_PT_EN_SOURCE_URL,
                license = "GNU GPL 2.0 or later",
                extractedFrom = "FreeDict Portuguese-English TEI source",
            )
        }
        if (config.freedictEnglishPortuguese != null) {
            insertSourceManifest(
                connection = connection,
                sourceId = FREEDICT_EN_PT_SOURCE_ID,
                sourceUrl = FREEDICT_EN_PT_SOURCE_URL,
                license = "GNU GPL 2.0 or later",
                extractedFrom = "FreeDict English-Portuguese TEI source inverted for EN->PT lookup",
            )
        }
        connection.commit()
    }

    private fun insertSourceManifest(
        connection: Connection,
        sourceId: String,
        sourceUrl: String,
        license: String,
        extractedFrom: String,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO source_manifest(source_id, source_url, license, extracted_from, imported_at_utc)
            VALUES (?, ?, ?, ?, datetime('now'))
            """.trimIndent(),
        ).use { insert ->
            insert.setString(1, sourceId)
            insert.setString(2, sourceUrl)
            insert.setString(3, license)
            insert.setString(4, extractedFrom)
            insert.executeUpdate()
        }
    }

    private fun importSources(connection: Connection) {
        var seen = 0
        var imported = 0
        RecordWriter(connection).use { writer ->
            openReader(config.input).useLines { lines ->
                for (line in lines) {
                    if (config.limit != null && seen >= config.limit) break
                    seen += 1

                    val node = mapper.readTree(line)
                    val record = node.toDictionaryRecord() ?: continue
                    writer.insert(record)
                    imported += 1

                    if (imported % BATCH_SIZE == 0) {
                        writer.flush()
                        connection.commit()
                        println("Imported $imported Kaikki entries")
                    }
                }
            }

            config.freedictPortugueseEnglish?.let { path ->
                val count = importFreeDictPortugueseEnglish(path, writer)
                writer.flush()
                connection.commit()
                println("Imported $count FreeDict PT-EN entries")
            }
            config.freedictEnglishPortuguese?.let { path ->
                val count = importFreeDictEnglishPortuguese(path, writer)
                writer.flush()
                connection.commit()
                println("Imported $count inverted FreeDict EN-PT entries")
            }

            writer.flush()
            connection.commit()
        }
        println("Imported $imported Kaikki entries from $seen JSONL records into ${config.output.absolutePathString()}")
    }

    private fun optimize(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE INDEX idx_entries_lemma ON entries(lemma)")
            statement.executeUpdate("CREATE INDEX idx_entries_commonality ON entries(commonality_score DESC)")
            statement.executeUpdate("CREATE INDEX idx_forms_form ON forms(form)")
            statement.executeUpdate("CREATE INDEX idx_forms_entry ON forms(entry_id)")
            statement.executeUpdate("CREATE INDEX idx_senses_entry ON senses(entry_id)")
            statement.executeUpdate("CREATE INDEX idx_senses_gloss_lc ON senses(gloss_lc)")
            statement.executeUpdate("CREATE INDEX idx_examples_entry ON examples(entry_id)")
            statement.executeUpdate("ANALYZE")
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
        val examples = path("senses")
            .filter(JsonNode::isObject)
            .flatMap { sense -> sense.path("examples").mapNotNull { it.toWordExample() } }
            .distinctBy { it.text }
            .take(MAX_EXAMPLES_PER_ENTRY)
        val frequencySignal = bestFrequencySignal(lemma, forms)

        return DictionaryRecord(
            lemma = lemma,
            pos = pos,
            sourceId = KAIKKI_SOURCE_ID,
            glosses = glosses,
            forms = forms,
            examples = examples,
            frequencySignal = frequencySignal,
        )
    }

    private fun JsonNode.toWordExample(): WordExample? {
        val text = path("text").asText("").cleanText()
        if (text.isBlank()) return null
        val translation = listOf(
            path("translation").asText("").cleanText(),
            path("english").asText("").cleanText(),
        ).firstOrNull { it.isNotBlank() }
        return WordExample(text = text, translation = translation)
    }

    private fun importFreeDictPortugueseEnglish(path: Path, writer: RecordWriter): Int {
        require(path.exists()) { "FreeDict PT-EN TEI does not exist: ${path.absolutePathString()}" }
        var imported = 0
        for (entry in parseTeiEntries(path)) {
            val lemma = entry.firstText("orth").cleanText()
            val glosses = entry.descendantTexts("quote").map { it.cleanText() }.filter(String::isNotBlank).distinct()
            if (lemma.isBlank() || glosses.isEmpty()) continue

            writer.insert(
                DictionaryRecord(
                    lemma = lemma,
                    pos = entry.firstText("pos").cleanText().ifBlank { "phrase" },
                    sourceId = FREEDICT_PT_EN_SOURCE_ID,
                    glosses = glosses,
                    forms = emptyList(),
                    examples = emptyList(),
                    frequencySignal = bestFrequencySignal(lemma, emptyList()),
                ),
            )
            imported += 1
        }
        return imported
    }

    private fun importFreeDictEnglishPortuguese(path: Path, writer: RecordWriter): Int {
        require(path.exists()) { "FreeDict EN-PT TEI does not exist: ${path.absolutePathString()}" }
        var imported = 0
        val seen = hashSetOf<String>()
        for (entry in parseTeiEntries(path)) {
            val englishLemma = entry.firstText("orth").cleanText()
            if (englishLemma.isBlank()) continue

            val portugueseLemmas = entry.descendantTexts("quote")
                .map { it.cleanText() }
                .filter(String::isNotBlank)
                .distinct()
            for (portugueseLemma in portugueseLemmas) {
                val key = "${portugueseLemma.lowercase(Locale.ROOT)}\u0000${englishLemma.lowercase(Locale.ROOT)}"
                if (!seen.add(key)) continue
                writer.insert(
                    DictionaryRecord(
                        lemma = portugueseLemma,
                        pos = "phrase",
                        sourceId = FREEDICT_EN_PT_SOURCE_ID,
                        glosses = listOf(englishLemma),
                        forms = emptyList(),
                        examples = emptyList(),
                        frequencySignal = bestFrequencySignal(portugueseLemma, emptyList()),
                    ),
                )
                imported += 1
            }
        }
        return imported
    }

    private fun parseTeiEntries(path: Path): Sequence<Element> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }

        val document = Files.newInputStream(path).use { input ->
            factory.newDocumentBuilder().parse(input)
        }
        val entries = document.getElementsByTagNameNS("*", "entry").takeIf { it.length > 0 }
            ?: document.getElementsByTagName("entry")
        return entries.asElementSequence()
    }

    private fun loadFrequencySignals(path: Path?): Map<String, FrequencySignal> {
        if (path == null || !path.exists()) return emptyMap()

        val signals = linkedMapOf<String, FrequencySignal>()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { index, line ->
                val columns = line.split('\t')
                if (columns.size >= 2) {
                    val word = columns[0].trim().lowercase(Locale.ROOT)
                    val frequency = columns[1].trim().toIntOrNull()
                    if (word.isNotBlank() && frequency != null && frequency > 0) {
                        val rank = index + 1
                        signals.putIfAbsent(
                            word,
                            FrequencySignal(
                                rank = rank,
                                frequency = frequency,
                                commonalityScore = commonalityScore(rank),
                                sourceId = FREQUENCY_SOURCE_ID,
                            ),
                        )
                    }
                }
            }
        }
        println("Loaded ${signals.size} frequency signals from ${path.absolutePathString()}")
        return signals
    }

    private fun bestFrequencySignal(lemma: String, forms: List<WordForm>): FrequencySignal? {
        val candidates = sequenceOf(lemma)
            .plus(forms.asSequence().map { it.text })
            .map { it.lowercase(Locale.ROOT) }
        return candidates.mapNotNull { frequencySignals[it] }.minByOrNull { it.rank }
    }

    private fun commonalityScore(rank: Int): Int {
        return when {
            rank <= 100 -> 260
            rank <= 500 -> 230
            rank <= 1_000 -> 210
            rank <= 2_500 -> 185
            rank <= 5_000 -> 160
            rank <= 10_000 -> 130
            rank <= 25_000 -> 95
            rank <= 50_000 -> 70
            rank <= 100_000 -> 45
            else -> 25
        }
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
        val sourceId: String,
        val glosses: List<String>,
        val forms: List<WordForm>,
        val examples: List<WordExample>,
        val frequencySignal: FrequencySignal?,
    )

    private data class WordForm(
        val text: String,
        val tags: List<String>,
    )

    private data class WordExample(
        val text: String,
        val translation: String?,
    )

    private data class FrequencySignal(
        val rank: Int,
        val frequency: Int,
        val commonalityScore: Int,
        val sourceId: String,
    )

    private inner class RecordWriter(private val connection: Connection) : AutoCloseable {
        private val insertEntry: PreparedStatement = connection.prepareStatement(
            """
            INSERT INTO entries(
                lemma,
                pos,
                source_id,
                commonality_score,
                frequency_rank,
                frequency,
                frequency_source_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("entry_id"),
        )
        private val insertSense = connection.prepareStatement(
            "INSERT INTO senses(entry_id, gloss, gloss_lc) VALUES (?, ?, ?)",
        )
        private val insertForm = connection.prepareStatement(
            "INSERT INTO forms(entry_id, form, tags) VALUES (?, ?, ?)",
        )
        private val insertExample = connection.prepareStatement(
            "INSERT INTO examples(entry_id, sentence, translation, source_id) VALUES (?, ?, ?, ?)",
        )
        private val insertSearch = connection.prepareStatement(
            "INSERT INTO search_fts(rowid, lemma, meanings, forms) VALUES (?, ?, ?, ?)",
        )

        fun insert(record: DictionaryRecord) {
            insertEntry.setString(1, record.lemma)
            insertEntry.setString(2, record.pos)
            insertEntry.setString(3, record.sourceId)
            insertEntry.setInt(4, record.frequencySignal?.commonalityScore ?: 0)
            if (record.frequencySignal == null) {
                insertEntry.setNull(5, Types.INTEGER)
                insertEntry.setNull(6, Types.INTEGER)
                insertEntry.setNull(7, Types.VARCHAR)
            } else {
                insertEntry.setInt(5, record.frequencySignal.rank)
                insertEntry.setInt(6, record.frequencySignal.frequency)
                insertEntry.setString(7, record.frequencySignal.sourceId)
            }
            insertEntry.executeUpdate()

            val entryId = insertEntry.generatedKeys.use { keys ->
                check(keys.next()) { "Missing generated entry id" }
                keys.getLong(1)
            }

            record.glosses.forEach { gloss ->
                insertSense.setLong(1, entryId)
                insertSense.setString(2, gloss)
                insertSense.setString(3, gloss.lowercase(Locale.ROOT))
                insertSense.addBatch()
            }
            record.forms.forEach { form ->
                insertForm.setLong(1, entryId)
                insertForm.setString(2, form.text)
                insertForm.setString(3, form.tags.joinToString(","))
                insertForm.addBatch()
            }
            record.examples.forEach { example ->
                insertExample.setLong(1, entryId)
                insertExample.setString(2, example.text)
                if (example.translation == null) {
                    insertExample.setNull(3, Types.VARCHAR)
                } else {
                    insertExample.setString(3, example.translation)
                }
                insertExample.setString(4, record.sourceId)
                insertExample.addBatch()
            }
            insertSearch.setLong(1, entryId)
            insertSearch.setString(2, record.lemma)
            insertSearch.setString(3, record.glosses.joinToString(" | "))
            insertSearch.setString(4, record.forms.joinToString(" ") { it.text })
            insertSearch.addBatch()
        }

        fun flush() {
            insertSense.executeBatch()
            insertForm.executeBatch()
            insertExample.executeBatch()
            insertSearch.executeBatch()
        }

        override fun close() {
            insertEntry.close()
            insertSense.close()
            insertForm.close()
            insertExample.close()
            insertSearch.close()
        }
    }

    private fun Element.firstText(localName: String): String {
        return descendantTexts(localName).firstOrNull().orEmpty()
    }

    private fun Element.descendantTexts(localName: String): List<String> {
        val nodes = getElementsByTagNameNS("*", localName).takeIf { it.length > 0 }
            ?: getElementsByTagName(localName)
        return nodes.asElementSequence()
            .map { it.textContent.orEmpty() }
            .toList()
    }

    private fun NodeList.asElementSequence(): Sequence<Element> {
        return sequence {
            for (index in 0 until length) {
                val node = item(index)
                if (node.nodeType == Node.ELEMENT_NODE) yield(node as Element)
            }
        }
    }

    private fun String.cleanText(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private const val BATCH_SIZE = 5_000
        private const val MAX_EXAMPLES_PER_ENTRY = 4
        private const val KAIKKI_SOURCE_ID = "kaikki-portuguese"
        private const val FREEDICT_PT_EN_SOURCE_ID = "freedict-por-eng"
        private const val FREEDICT_EN_PT_SOURCE_ID = "freedict-eng-por"
        private const val FREQUENCY_SOURCE_ID = "hf-eu-pt-web-frequency"
        private const val FREEDICT_PT_EN_SOURCE_URL = "https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.src.tar.bz2"
        private const val FREEDICT_EN_PT_SOURCE_URL = "https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.src.tar.xz"
    }
}
