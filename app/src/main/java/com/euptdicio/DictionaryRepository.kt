package com.euptdicio

import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteDatabase
import com.euptdicio.core.DictionaryEntry
import com.euptdicio.core.LookupDirection
import com.euptdicio.core.LookupResult
import com.euptdicio.core.MatchType
import com.euptdicio.core.PartOfSpeech
import com.euptdicio.core.PortugueseNormalizer
import java.io.File

class DictionaryRepository(private val context: Context) {
    private val database: SQLiteDatabase by lazy {
        openDatabase()
    }

    fun lookup(
        query: String,
        direction: LookupDirection,
        sortMode: SortMode,
        limit: Int = 20,
    ): List<LookupResult> {
        val normalized = PortugueseNormalizer.normalizeForLookup(query)
        if (normalized.isBlank()) return emptyList()

        val results = when (direction) {
            LookupDirection.PortugueseToEnglish -> lookupPortuguese(normalized, limit)
            LookupDirection.EnglishToPortuguese -> lookupEnglish(query.trim(), limit)
        }
        return when (sortMode) {
            SortMode.Popularity -> results
            SortMode.Alphabetical -> results.sortedBy { it.entry.lemma.lowercase() }
        }
    }

    fun debugStatus(): DictionaryDebugStatus {
        val asset = assetStatus()
        val local = localDatabaseFile()
        val localExists = local.exists()
        val localBytes = if (localExists) local.length() else 0L
        var schemaOk = false
        var ftsOk = false
        var entryCount: Long? = null
        var frequencySignalCount: Long? = null
        var message = "OK"

        runCatching {
            val db = openDatabase()
            try {
                db.rawQuery("SELECT COUNT(*) FROM entries", emptyArray()).use { cursor ->
                    schemaOk = cursor.moveToFirst()
                    entryCount = if (schemaOk) cursor.getLong(0) else null
                }
                db.rawQuery("SELECT COUNT(*) FROM entries WHERE commonality_score > 0", emptyArray()).use { cursor ->
                    frequencySignalCount = if (cursor.moveToFirst()) cursor.getLong(0) else null
                }
                ftsOk = runCatching {
                    db.rawQuery("SELECT rowid FROM search_fts LIMIT 1", emptyArray()).use { it.moveToFirst() }
                }.isSuccess
            } finally {
                db.close()
            }
        }.onFailure { error ->
            message = error.message ?: error::class.java.simpleName
        }

        return DictionaryDebugStatus(
            assetPresent = asset.present,
            assetBytes = asset.bytes,
            localPresent = localExists,
            localBytes = localBytes,
            schemaOk = schemaOk,
            ftsOk = ftsOk,
            entryCount = entryCount,
            frequencySignalCount = frequencySignalCount,
            message = message,
        )
    }

    private fun lookupPortuguese(query: String, limit: Int): List<LookupResult> {
        val rows = linkedMapOf<Long, SearchHit>()
        queryExactLemma(query, limit, rows)
        queryExactForm(query, limit, rows)
        queryFts(query.toFtsPrefixExpression(), limit, rows, MatchType.Prefix, 520)
        if (rows.size < limit) {
            queryPortuguesePrefix(query, limit, rows)
        }
        if (rows.size < limit) {
            queryFts(
                PortugueseNormalizer.stripAccents(query).toFtsPrefixExpression(),
                limit,
                rows,
                MatchType.AccentInsensitive,
                470,
            )
        }
        return hydrate(rows.bestHits(limit))
    }

    private fun lookupEnglish(query: String, limit: Int): List<LookupResult> {
        val clean = query.lowercase()
        val rows = linkedMapOf<Long, SearchHit>()
        queryEnglishMeaning(clean, limit, rows)
        queryFts(clean.toFtsPrefixExpression(), limit, rows, MatchType.EnglishMeaning, 610)
        if (rows.size < limit) {
            queryEnglishPrefix(clean, limit, rows)
        }
        return hydrate(rows.bestHits(limit))
    }

    private fun queryExactLemma(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        database.rawQuery(
            """
            SELECT entry_id, lemma, pos, commonality_score
            FROM entries
            WHERE lemma = ?
            LIMIT ?
            """.trimIndent(),
            arrayOf(query, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    id = cursor.getLong(0),
                    hit = SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.ExactLemma,
                        score = rankedScore(
                            base = 1000,
                            lemma = cursor.getString(1),
                            pos = cursor.getString(2),
                            commonalityScore = cursor.getInt(3),
                        ),
                    ),
                )
            }
        }
    }

    private fun queryExactForm(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        database.rawQuery(
            """
            SELECT f.entry_id, f.form, e.lemma, e.pos, e.commonality_score
            FROM forms f
            JOIN entries e ON e.entry_id = f.entry_id
            WHERE f.form = ?
            LIMIT ?
            """.trimIndent(),
            arrayOf(query, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    id = cursor.getLong(0),
                    hit = SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.InflectedForm,
                        score = rankedScore(
                            base = 900,
                            lemma = cursor.getString(2),
                            pos = cursor.getString(3),
                            commonalityScore = cursor.getInt(4),
                        ),
                    ),
                )
            }
        }
    }

    private fun queryEnglishMeaning(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        database.rawQuery(
            """
            SELECT e.entry_id, s.gloss, e.lemma, e.pos, e.commonality_score,
                CASE
                    WHEN lower(s.gloss) = ? THEN 940
                    WHEN lower(s.gloss) LIKE ? THEN 930
                    WHEN lower(s.gloss) LIKE ? THEN 910
                    WHEN lower(s.gloss) LIKE ? THEN 900
                    WHEN lower(s.gloss) LIKE ? THEN 860
                    WHEN lower(s.gloss) LIKE ? THEN 620
                    ELSE 0
                END AS match_score
            FROM senses s
            JOIN entries e ON e.entry_id = s.entry_id
            WHERE lower(s.gloss) = ?
                OR lower(s.gloss) LIKE ?
                OR lower(s.gloss) LIKE ?
                OR lower(s.gloss) LIKE ?
                OR lower(s.gloss) LIKE ?
                OR lower(s.gloss) LIKE ?
            ORDER BY match_score DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                query,
                "$query (%",
                "$query,%",
                "$query;%",
                "$query %",
                "$query%",
                query,
                "$query (%",
                "$query,%",
                "$query;%",
                "$query %",
                "$query%",
                limit.toString(),
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    id = cursor.getLong(0),
                    hit = SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.EnglishMeaning,
                        score = rankedScore(
                            base = cursor.getInt(5),
                            lemma = cursor.getString(2),
                            pos = cursor.getString(3),
                            commonalityScore = cursor.getInt(4),
                        ),
                    ),
                )
            }
        }
    }

    private fun queryPortuguesePrefix(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        database.rawQuery(
            """
            SELECT entry_id, lemma, pos, commonality_score, 500 AS score
            FROM entries
            WHERE lemma LIKE ?
            UNION ALL
            SELECT f.entry_id, f.form, e.pos, e.commonality_score, 470 AS score
            FROM forms f
            JOIN entries e ON e.entry_id = f.entry_id
            WHERE f.form LIKE ?
            LIMIT ?
            """.trimIndent(),
            arrayOf("$query%", "$query%", limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    id = cursor.getLong(0),
                    hit = SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.Prefix,
                        score = rankedScore(
                            base = cursor.getInt(4),
                            lemma = cursor.getString(1),
                            pos = cursor.getString(2),
                            commonalityScore = cursor.getInt(3),
                        ),
                    ),
                )
            }
        }
    }

    private fun queryEnglishPrefix(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        database.rawQuery(
            """
            SELECT e.entry_id, s.gloss, e.lemma, e.pos, e.commonality_score,
                CASE
                    WHEN lower(s.gloss) LIKE ? THEN 500
                    WHEN lower(s.gloss) LIKE ? THEN 420
                    ELSE 0
                END AS match_score
            FROM senses s
            JOIN entries e ON e.entry_id = s.entry_id
            WHERE lower(s.gloss) LIKE ? OR lower(s.gloss) LIKE ?
            ORDER BY match_score DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf("$query %", "% $query%", "$query %", "% $query%", limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    id = cursor.getLong(0),
                    hit = SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.EnglishMeaning,
                        score = rankedScore(
                            base = cursor.getInt(5),
                            lemma = cursor.getString(2),
                            pos = cursor.getString(3),
                            commonalityScore = cursor.getInt(4),
                        ),
                    ),
                )
            }
        }
    }

    private fun queryFts(
        expression: String,
        limit: Int,
        rows: MutableMap<Long, SearchHit>,
        matchType: MatchType,
        score: Int,
    ) {
        if (expression.isBlank()) return

        runCatching {
            database.rawQuery(
                """
                SELECT search_fts.rowid, search_fts.lemma, entries.pos, entries.commonality_score
                FROM search_fts
                JOIN entries ON entries.entry_id = search_fts.rowid
                WHERE search_fts MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(expression, limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows.putBest(
                        id = cursor.getLong(0),
                        hit = SearchHit(
                            entryId = cursor.getLong(0),
                            matchedForm = cursor.getString(1),
                            matchType = matchType,
                            score = rankedScore(
                                base = score,
                                lemma = cursor.getString(1),
                                pos = cursor.getString(2),
                                commonalityScore = cursor.getInt(3),
                            ),
                        ),
                    )
                }
            }
        }.onFailure {
            if (it !is SQLiteException) throw it
            // Older or vendor-modified Android SQLite builds may not support this FTS table.
        }
    }

    private fun hydrate(hits: List<SearchHit>): List<LookupResult> {
        if (hits.isEmpty()) return emptyList()
        val ids = hits.map { it.entryId }
        val entries = loadEntries(ids)
        val meanings = loadMeanings(ids)
        val forms = loadForms(ids)

        return hits.mapNotNull { hit ->
            val base = entries[hit.entryId] ?: return@mapNotNull null
            LookupResult(
                entry = base.copy(
                    meanings = meanings[hit.entryId].orEmpty().take(6),
                    forms = forms[hit.entryId].orEmpty().take(24),
                ),
                matchedForm = hit.matchedForm,
                matchType = hit.matchType,
                score = hit.score,
            )
        }
    }

    private fun loadEntries(ids: List<Long>): Map<Long, DictionaryEntry> {
        val placeholders = ids.joinToString(",") { "?" }
        return database.rawQuery(
            """
            SELECT entry_id, lemma, pos, source_id, commonality_score, frequency_rank
            FROM entries
            WHERE entry_id IN ($placeholders)
            """.trimIndent(),
            ids.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getLong(0),
                        DictionaryEntry(
                            lemma = cursor.getString(1),
                            partOfSpeech = cursor.getString(2).toPartOfSpeech(),
                            meanings = emptyList(),
                            forms = emptyList(),
                            labels = buildList {
                                add(cursor.getString(3))
                                if (cursor.getInt(4) > 0 && !cursor.isNull(5)) {
                                    add("rank ${cursor.getInt(5)}")
                                }
                            },
                            source = cursor.getString(3),
                        ),
                    )
                }
            }
        }
    }

    private fun loadMeanings(ids: List<Long>): Map<Long, List<String>> {
        val placeholders = ids.joinToString(",") { "?" }
        return database.rawQuery(
            """
            SELECT entry_id, gloss
            FROM senses
            WHERE entry_id IN ($placeholders)
            ORDER BY sense_id
            """.trimIndent(),
            ids.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildGroupedStrings(cursor)
        }
    }

    private fun loadForms(ids: List<Long>): Map<Long, List<String>> {
        val placeholders = ids.joinToString(",") { "?" }
        return database.rawQuery(
            """
            SELECT entry_id, form
            FROM forms
            WHERE entry_id IN ($placeholders)
            ORDER BY form
            """.trimIndent(),
            ids.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildGroupedStrings(cursor)
        }
    }

    private fun buildGroupedStrings(cursor: android.database.Cursor): Map<Long, List<String>> {
        val grouped = linkedMapOf<Long, MutableList<String>>()
        while (cursor.moveToNext()) {
            grouped.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(cursor.getString(1))
        }
        return grouped
    }

    private fun ensureDatabaseCopied(): File {
        val destination = localDatabaseFile()
        val asset = assetStatus()
        if (
            destination.exists() &&
            destination.length() > MIN_DATABASE_BYTES &&
            asset.bytes != null &&
            destination.length() == asset.bytes &&
            isSchemaReadable(destination)
        ) {
            return destination
        }

        destination.parentFile?.mkdirs()
        destination.delete()

        context.assets.open(ASSET_PATH).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destination
    }

    private fun openDatabase(): SQLiteDatabase {
        val file = ensureDatabaseCopied()
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun localDatabaseFile(): File {
        return File(context.noBackupFilesDir, DATABASE_NAME)
    }

    private fun isSchemaReadable(file: File): Boolean {
        return runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM entries", emptyArray()).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) > 100_000
                }
            }
        }.getOrDefault(false)
    }

    private fun assetStatus(): AssetStatus {
        return runCatching {
            context.assets.openFd(ASSET_PATH).use { descriptor ->
                AssetStatus(present = true, bytes = descriptor.length)
            }
        }.recoverCatching {
            context.assets.open(ASSET_PATH).use { input ->
                AssetStatus(present = true, bytes = input.available().toLong())
            }
        }.getOrElse {
            AssetStatus(present = false, bytes = null)
        }
    }

    private fun MutableMap<Long, SearchHit>.putBest(id: Long, hit: SearchHit) {
        val existing = this[id]
        if (existing == null || hit.score > existing.score) {
            this[id] = hit
        }
    }

    private fun MutableMap<Long, SearchHit>.bestHits(limit: Int): List<SearchHit> {
        return values
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.matchedForm })
            .take(limit)
    }

    private fun String.toFtsPrefixExpression(): String {
        return Regex("[\\p{L}\\p{N}]+")
            .findAll(this)
            .map { it.value.replace("\"", "\"\"") }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"*" }
    }

    private fun String.toPartOfSpeech(): PartOfSpeech {
        return when (lowercase()) {
            "noun", "name", "proper-noun", "num" -> PartOfSpeech.Noun
            "verb" -> PartOfSpeech.Verb
            "adj", "adjective" -> PartOfSpeech.Adjective
            "adv", "adverb" -> PartOfSpeech.Adverb
            else -> PartOfSpeech.Phrase
        }
    }

    private fun rankedScore(
        base: Int,
        lemma: String,
        pos: String,
        commonalityScore: Int,
    ): Int {
        return base +
            commonalityScore +
            COMMON_LEMMA_BONUS[lemma.lowercase()].orZero() +
            partOfSpeechBonus(pos) -
            multiwordPenalty(lemma) -
            rareShapePenalty(lemma, pos)
    }

    private fun partOfSpeechBonus(pos: String): Int {
        return when (pos.lowercase()) {
            "noun", "verb", "adj", "adjective", "adv", "adverb" -> 30
            "name", "proper-noun" -> -180
            else -> 0
        }
    }

    private fun multiwordPenalty(lemma: String): Int {
        val separators = lemma.count { it == ' ' || it == '-' }
        return separators * 18
    }

    private fun rareShapePenalty(lemma: String, pos: String): Int {
        if (pos.equals("name", ignoreCase = true)) return 120
        if (lemma.any(Char::isUpperCase)) return 50
        if (lemma.length > 18) return 35
        return 0
    }

    private fun Int?.orZero(): Int = this ?: 0

    private data class SearchHit(
        val entryId: Long,
        val matchedForm: String,
        val matchType: MatchType,
        val score: Int,
    )

    private data class AssetStatus(
        val present: Boolean,
        val bytes: Long?,
    )

    private companion object {
        const val ASSET_PATH = "dictionary/euptdicio.sqlite"
        const val DATABASE_NAME = "euptdicio.sqlite"
        const val MIN_DATABASE_BYTES = 100_000_000L

        val COMMON_LEMMA_BONUS = mapOf(
            "gato" to 180,
            "gata" to 120,
            "cão" to 180,
            "casa" to 170,
            "água" to 170,
            "pessoa" to 160,
            "homem" to 160,
            "mulher" to 160,
            "criança" to 160,
            "comida" to 150,
            "tempo" to 150,
            "dia" to 150,
            "noite" to 150,
            "falar" to 150,
            "fazer" to 150,
            "ir" to 150,
            "ser" to 150,
            "estar" to 150,
            "ter" to 150,
            "bom" to 140,
            "mau" to 140,
            "grande" to 130,
            "pequeno" to 130,
        )
    }
}

enum class SortMode {
    Popularity,
    Alphabetical,
}

data class DictionaryDebugStatus(
    val assetPresent: Boolean,
    val assetBytes: Long?,
    val localPresent: Boolean,
    val localBytes: Long,
    val schemaOk: Boolean,
    val ftsOk: Boolean,
    val entryCount: Long?,
    val frequencySignalCount: Long?,
    val message: String,
)
