package com.euptdicio.wear

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.euptdicio.core.DictionaryEntry
import com.euptdicio.core.DictionaryExample
import com.euptdicio.core.DictionaryForm
import com.euptdicio.core.LookupDirection
import com.euptdicio.core.LookupResult
import com.euptdicio.core.MatchType
import com.euptdicio.core.PartOfSpeech
import com.euptdicio.core.PortugueseNormalizer
import java.io.File
import java.util.Locale

class WearDictionaryRepository(private val context: Context) {
    private val database: SQLiteDatabase by lazy {
        openDatabase()
    }

    fun lookup(
        query: String,
        direction: LookupDirection,
        limit: Int = 8,
    ): List<LookupResult> {
        val normalized = PortugueseNormalizer.normalizeForLookup(query)
        if (normalized.isBlank()) return emptyList()

        val hits = when (direction) {
            LookupDirection.PortugueseToEnglish -> lookupPortuguese(normalized, limit)
            LookupDirection.EnglishToPortuguese -> lookupEnglish(query.trim(), limit)
        }
        return hydrate(hits.bestHits(limit))
    }

    private fun lookupPortuguese(query: String, limit: Int): MutableMap<Long, SearchHit> {
        val rows = linkedMapOf<Long, SearchHit>()
        queryExactLemma(query, limit, rows)
        queryExactForm(query, limit, rows)
        queryFts(
            query.toFtsPrefixExpression("lemma", "forms"),
            limit * FTS_CANDIDATE_MULTIPLIER,
            rows,
            MatchType.Prefix,
            520,
        )
        if (rows.size < limit) {
            queryFts(
                PortugueseNormalizer.stripAccents(query).toFtsPrefixExpression("lemma", "forms"),
                limit * FTS_CANDIDATE_MULTIPLIER,
                rows,
                MatchType.AccentInsensitive,
                470,
            )
        }
        if (rows.size < limit) {
            queryPortuguesePrefix(query, limit, rows)
        }
        return rows
    }

    private fun lookupEnglish(query: String, limit: Int): MutableMap<Long, SearchHit> {
        val clean = query.lowercase(Locale.ROOT)
        val rows = linkedMapOf<Long, SearchHit>()
        queryEnglishMeaning(clean, limit, rows)
        queryFts(
            clean.toFtsPrefixExpression("meanings"),
            limit * FTS_CANDIDATE_MULTIPLIER,
            rows,
            MatchType.EnglishMeaning,
            610,
        )
        if (rows.size < limit) {
            queryEnglishPrefix(clean, limit, rows)
        }
        return rows
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
                    cursor.getLong(0),
                    SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.ExactLemma,
                        score = rankedScore(cursor.getString(1), cursor.getString(2), cursor.getInt(3), 1000),
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
                    cursor.getLong(0),
                    SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.InflectedForm,
                        score = rankedScore(cursor.getString(2), cursor.getString(3), cursor.getInt(4), 900),
                    ),
                )
            }
        }
    }

    private fun queryEnglishMeaning(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        val upperBound = query + "\uffff"
        database.rawQuery(
            """
            SELECT e.entry_id, s.gloss, e.lemma, e.pos, e.commonality_score,
                CASE WHEN s.gloss_lc = ? THEN 940 ELSE 860 END AS match_score
            FROM senses s
            JOIN entries e ON e.entry_id = s.entry_id
            WHERE s.gloss_lc >= ? AND s.gloss_lc < ?
            ORDER BY match_score DESC, e.commonality_score DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(query, query, upperBound, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    cursor.getLong(0),
                    SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.EnglishMeaning,
                        score = rankedScore(cursor.getString(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5)),
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
                    cursor.getLong(0),
                    SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.Prefix,
                        score = rankedScore(cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4)),
                    ),
                )
            }
        }
    }

    private fun queryEnglishPrefix(query: String, limit: Int, rows: MutableMap<Long, SearchHit>) {
        val upperBound = query + "\uffff"
        database.rawQuery(
            """
            SELECT e.entry_id, s.gloss, e.lemma, e.pos, e.commonality_score
            FROM senses s
            JOIN entries e ON e.entry_id = s.entry_id
            WHERE s.gloss_lc >= ? AND s.gloss_lc < ?
            ORDER BY e.commonality_score DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(query, upperBound, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.putBest(
                    cursor.getLong(0),
                    SearchHit(
                        entryId = cursor.getLong(0),
                        matchedForm = cursor.getString(1),
                        matchType = MatchType.EnglishMeaning,
                        score = rankedScore(cursor.getString(2), cursor.getString(3), cursor.getInt(4), 500),
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
                ORDER BY entries.commonality_score DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(expression, limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows.putBest(
                        cursor.getLong(0),
                        SearchHit(
                            entryId = cursor.getLong(0),
                            matchedForm = cursor.getString(1),
                            matchType = matchType,
                            score = rankedScore(cursor.getString(1), cursor.getString(2), cursor.getInt(3), score),
                        ),
                    )
                }
            }
        }.onFailure {
            if (it !is SQLiteException) throw it
        }
    }

    private fun hydrate(hits: List<SearchHit>): List<LookupResult> {
        if (hits.isEmpty()) return emptyList()
        val ids = hits.map { it.entryId }
        val entries = loadEntries(ids)
        val meanings = loadMeanings(ids)
        val forms = loadForms(ids)
        return hits.mapNotNull { hit ->
            val entry = entries[hit.entryId] ?: return@mapNotNull null
            LookupResult(
                entryId = hit.entryId,
                entry = entry.copy(
                    meanings = meanings[hit.entryId].orEmpty().take(MAX_MEANINGS_PER_ENTRY),
                    forms = forms[hit.entryId].orEmpty().take(MAX_FORMS_PER_ENTRY),
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
                            examples = emptyList<DictionaryExample>(),
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

    private fun loadForms(ids: List<Long>): Map<Long, List<DictionaryForm>> {
        val placeholders = ids.joinToString(",") { "?" }
        return database.rawQuery(
            """
            SELECT entry_id, form, tags
            FROM forms
            WHERE entry_id IN ($placeholders)
            ORDER BY entry_id, tags, form
            """.trimIndent(),
            ids.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            val grouped = linkedMapOf<Long, MutableList<DictionaryForm>>()
            while (cursor.moveToNext()) {
                val forms = grouped.getOrPut(cursor.getLong(0)) { mutableListOf() }
                if (forms.size < MAX_FORMS_PER_ENTRY) {
                    forms.add(
                        DictionaryForm(
                            text = cursor.getString(1),
                            tags = cursor.getString(2).split(',').filter(String::isNotBlank),
                        ),
                    )
                }
            }
            grouped
        }
    }

    private fun ensureDatabaseCopied(): File {
        val destination = File(context.noBackupFilesDir, DATABASE_NAME)
        if (destination.exists() && destination.length() > MIN_DATABASE_BYTES && isSchemaReadable(destination)) {
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
        return SQLiteDatabase.openDatabase(
            ensureDatabaseCopied().absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).also(::configureReadOnlyDatabase)
    }

    private fun configureReadOnlyDatabase(database: SQLiteDatabase) {
        listOf(
            "PRAGMA temp_store = MEMORY",
            "PRAGMA cache_size = -4096",
            "PRAGMA mmap_size = 134217728",
            "PRAGMA query_only = ON",
        ).forEach { pragma ->
            runCatching { database.execSQL(pragma) }
        }
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

    private fun buildGroupedStrings(cursor: Cursor): Map<Long, List<String>> {
        val grouped = linkedMapOf<Long, MutableList<String>>()
        while (cursor.moveToNext()) {
            grouped.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(cursor.getString(1))
        }
        return grouped
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

    private fun String.toFtsPrefixExpression(vararg columns: String): String {
        val tokens = Regex("[\\p{L}\\p{N}]+")
            .findAll(this)
            .map { it.value.replace("\"", "\"\"") }
            .filter { it.isNotBlank() }
            .toList()

        return tokens.joinToString(" ") { token ->
            columns.joinToString(" OR ") { column -> "$column:$token*" }
        }
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

    private fun rankedScore(lemma: String, pos: String, commonalityScore: Int, base: Int): Int {
        return base + commonalityScore + COMMON_LEMMA_BONUS[lemma.lowercase()].orZero() + partOfSpeechBonus(pos) -
            multiwordPenalty(lemma) - rareShapePenalty(lemma, pos)
    }

    private fun partOfSpeechBonus(pos: String): Int {
        return when (pos.lowercase()) {
            "noun", "verb", "adj", "adjective", "adv", "adverb" -> 30
            "name", "proper-noun" -> -180
            else -> 0
        }
    }

    private fun multiwordPenalty(lemma: String): Int = lemma.count { it == ' ' || it == '-' } * 18

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

    private companion object {
        const val ASSET_PATH = "dictionary/euptdicio.sqlite"
        const val DATABASE_NAME = "euptdicio.sqlite"
        const val MIN_DATABASE_BYTES = 100_000_000L
        const val FTS_CANDIDATE_MULTIPLIER = 4
        const val MAX_MEANINGS_PER_ENTRY = 4
        const val MAX_FORMS_PER_ENTRY = 12

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
            "falar" to 150,
            "fazer" to 150,
            "ser" to 150,
            "estar" to 150,
            "ter" to 150,
        )
    }
}
