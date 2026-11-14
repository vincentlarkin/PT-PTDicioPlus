package com.euptdicio.core

/**
 * All dictionary queries. Every query is an index range or point lookup, so search stays in the
 * low-millisecond range on a phone even with ~750k lookup keys.
 */
class Dictionary(private val db: SqlDb) {

    private val summaryCols = """
        e.id, e.word, e.pos, e.gender, e.ipa, e.variety, e.flags, e.rank, e.score,
        (SELECT s.gloss FROM sense s WHERE s.entry_id = e.id ORDER BY s.ord LIMIT 1)
    """.trimIndent()

    private fun Row.summary(offset: Int = 0) = EntrySummary(
        id = long(offset), word = string(offset + 1), pos = string(offset + 2), gender = stringOrNull(offset + 3),
        ipa = stringOrNull(offset + 4), variety = int(offset + 5), flags = int(offset + 6),
        rank = intOrNull(offset + 7), score = int(offset + 8), firstGloss = stringOrNull(offset + 9) ?: "",
    )

    private val tagsetCache = HashMap<Int, String>()

    private fun tags(id: Int): String? {
        if (id == 0) return null
        return tagsetCache.getOrPut(id) {
            db.query("SELECT tags FROM tagset WHERE id = ?", listOf(id)) { it.string(0) }.firstOrNull() ?: ""
        }
    }

    // ---------------------------------------------------------------- search

    fun search(raw: String, limit: Int = 30): SearchResults {
        val start = System.nanoTime()
        val query = raw.trim()
        if (query.isEmpty()) return SearchResults(query, emptyList(), emptyList(), false, 0)
        val pt = searchPortuguese(query, limit)
        val en = searchEnglish(query, limit)
        val ptExact = pt.any { it.match.kind != MatchKind.Prefix }
        val enExact = en.any { it.match.kind == MatchKind.English }
        val englishFirst = when {
            enExact && !ptExact -> true
            ptExact && !enExact -> false
            enExact && ptExact -> en.first().entry.score + 300 > pt.first().entry.score + 200 &&
                pt.first().match.kind != MatchKind.Headword
            else -> false
        }
        return SearchResults(query, pt, en, englishFirst, (System.nanoTime() - start) / 1000)
    }

    fun searchPortuguese(query: String, limit: Int = 30): List<SearchHit> {
        val key = PortugueseText.key(query)
        if (key.isEmpty()) return emptyList()
        val hits = LinkedHashMap<Long, SearchHit>()
        exactLookup(key, query).forEach { hits.putIfAbsent(it.entry.id, it) }
        if (hits.isEmpty()) {
            // Clitics only attach to verbs: dá-me -> dá (dar), not the contraction "da".
            val verbPart = query.trim().substringBefore('-')
            for (candidate in PortugueseText.cliticCandidates(query)) {
                val typed = verbPart.takeIf { PortugueseText.key(it) == candidate }
                exactLookup(candidate, typed).filter { it.entry.pos == "verb" }.forEach {
                    hits.putIfAbsent(it.entry.id, it.copy(match = it.match.copy(kind = MatchKind.Clitic, note = query)))
                }
                if (hits.isNotEmpty()) break
            }
        }
        if (hits.size < limit) {
            prefix(key, limit).forEach { hits.putIfAbsent(it.entry.id, it) }
        }
        return hits.values.take(limit)
    }

    private fun exactLookup(key: String, typed: String?): List<SearchHit> {
        val rows = db.query(
            """
            SELECT l.kind, coalesce(l.text, l.key), l.tagset, $summaryCols
            FROM lookup l JOIN entry e ON e.id = l.entry_id
            WHERE l.key = ?
            """.trimIndent(),
            listOf(key),
        ) { r -> Triple(r.int(0), r.string(1), r.int(2)) to r.summary(3) }
        // Per entry keep the best reason; order: exact accents typed > match kind > popularity.
        val best = LinkedHashMap<Long, Pair<Triple<Int, String, Int>, EntrySummary>>()
        for (row in rows) {
            val cur = best[row.second.id]
            if (cur == null || rank(row.first, typed) < rank(cur.first, typed)) best[row.second.id] = row
        }
        return best.values
            .sortedWith(compareBy({ rank(it.first, typed) }, { -it.second.score }))
            .map { (m, e) -> SearchHit(e, MatchInfo(MatchKind.of(m.first), m.second, tags(m.third))) }
    }

    private fun rank(m: Triple<Int, String, Int>, typed: String?): Int {
        val exactAccents = typed != null && m.second.equals(typed, ignoreCase = true)
        return (if (exactAccents) 0 else 10) + m.first
    }

    private fun prefix(key: String, limit: Int): List<SearchHit> = db.query(
        """
        SELECT $summaryCols FROM entry e
        WHERE e.norm >= ? AND e.norm < ? AND e.norm != ?
        ORDER BY e.score DESC LIMIT ?
        """.trimIndent(),
        listOf(key, PortugueseText.prefixUpperBound(key), key, limit),
    ) { r -> SearchHit(r.summary(), MatchInfo(MatchKind.Prefix, r.string(1))) }

    fun searchEnglish(query: String, limit: Int = 30): List<SearchHit> {
        var term = query.trim().lowercase().replace(Regex("\\s+"), " ")
        if (term.startsWith("to ") && term.length > 3) term = term.substring(3)
        if (term.isEmpty()) return emptyList()
        val terms = LinkedHashSet<String>().apply {
            add(term)
            addAll(db.query("SELECT lemma FROM en_form WHERE form = ?", listOf(term)) { it.string(0) })
        }
        val marks = terms.joinToString(",") { "?" }
        val exact = db.query(
            """
            SELECT x.term, x.weight, x.note, (SELECT s.gloss FROM sense s WHERE s.id = x.sense_id), $summaryCols
            FROM en_index x JOIN entry e ON e.id = x.entry_id
            WHERE x.term IN ($marks)
            ORDER BY x.weight * 6 + e.score DESC
            LIMIT ?
            """.trimIndent(),
            terms.toList() + (limit * 2),
        ) { r ->
            val e = r.summary(4)
            SearchHit(
                r.stringOrNull(3)?.let { e.copy(firstGloss = it) } ?: e,
                MatchInfo(MatchKind.English, r.string(0), note = r.stringOrNull(2),
                    detail = if (r.string(0) != term) "from \"${r.string(0)}\"" else null),
            )
        }
        val seen = HashSet<Long>()
        val out = exact.filter { seen.add(it.entry.id) }.toMutableList()
        if (out.size < 8 && term.length >= 3) {
            // English prefix: "breakf" -> breakfast. Distinct terms first, best entry per term.
            val more = db.query(
                """
                SELECT x.term, x.weight, x.note, (SELECT s.gloss FROM sense s WHERE s.id = x.sense_id), $summaryCols
                FROM en_index x JOIN entry e ON e.id = x.entry_id
                WHERE x.term > ? AND x.term < ? AND x.weight >= 80
                ORDER BY length(x.term), x.weight * 6 + e.score DESC
                LIMIT 40
                """.trimIndent(),
                listOf(term, PortugueseText.prefixUpperBound(term)),
            ) { r ->
                val e = r.summary(4)
                SearchHit(r.stringOrNull(3)?.let { e.copy(firstGloss = it) } ?: e,
                    MatchInfo(MatchKind.EnglishPrefix, r.string(0), note = r.stringOrNull(2)))
            }
            for (h in more) if (out.size < limit && seen.add(h.entry.id)) out += h
        }
        return out.take(limit)
    }

    /** Fast headword completions for the search field. */
    fun suggest(prefix: String, limit: Int = 8): List<EntrySummary> {
        val key = PortugueseText.key(prefix)
        if (key.isEmpty()) return emptyList()
        return db.query(
            "SELECT $summaryCols FROM entry e WHERE e.norm >= ? AND e.norm < ? ORDER BY e.score DESC LIMIT ?",
            listOf(key, PortugueseText.prefixUpperBound(key), limit),
        ) { it.summary() }
    }

    // ---------------------------------------------------------------- entry detail

    fun summary(id: Long): EntrySummary? =
        db.query("SELECT $summaryCols FROM entry e WHERE e.id = ?", listOf(id)) { it.summary() }.firstOrNull()

    fun entry(id: Long): EntryDetail? {
        data class Extra(
            val alt: String?, val head: String?, val ipaBr: String?, val audio: String?, val hyph: String?,
            val etym: String?, val freq: Double, val sourceId: Long,
        )
        val (summary, extra) = db.query(
            "SELECT $summaryCols, e.alt_word, e.head, e.ipa_br, e.audio, e.hyph, e.etym, e.freq, e.source_id FROM entry e WHERE e.id = ?",
            listOf(id),
        ) { r ->
            r.summary() to Extra(r.stringOrNull(10), r.stringOrNull(11), r.stringOrNull(12), r.stringOrNull(13),
                r.stringOrNull(14), r.stringOrNull(15), r.double(16), r.long(17))
        }.firstOrNull() ?: return null

        val sources = db.query("SELECT id, code, license, attribution, url FROM source", emptyList()) { r ->
            r.long(0) to Source(r.string(1), r.string(2), r.string(3), r.string(4))
        }.toMap()

        val examples = db.query(
            "SELECT sense_id, pt, en, source_id FROM example WHERE entry_id = ? ORDER BY id", listOf(id),
        ) { r -> r.longOrNull(0) to Example(r.string(1), r.stringOrNull(2), sources[r.long(3)]?.code ?: "") }
            .groupBy({ it.first }, { it.second })

        val rawRels = db.query(
            "SELECT sense_id, kind, word, labels FROM rel WHERE entry_id = ?", listOf(id),
        ) { r -> Triple(r.longOrNull(0), r.string(1), Relation(r.string(2), r.labelList(3), null)) }
        val targets = resolveMany(rawRels.map { it.third.word })
        val rels = rawRels.map { (sid, kind, rel) -> Triple(sid, kind, rel.copy(entryId = targets[rel.word])) }

        val senses = db.query(
            "SELECT id, gloss, labels, topic, variety FROM sense WHERE entry_id = ? ORDER BY ord", listOf(id),
        ) { r ->
            val sid = r.long(0)
            Sense(
                id = sid, gloss = r.string(1), labels = r.labelList(2), topic = r.stringOrNull(3), variety = r.int(4),
                examples = examples[sid].orEmpty(),
                synonyms = rels.filter { it.first == sid && it.second == "synonym" }.map { it.third },
                antonyms = rels.filter { it.first == sid && it.second == "antonym" }.map { it.third },
            )
        }

        val forms = db.query(
            "SELECT f.form, coalesce(t.tags, '') FROM form f LEFT JOIN tagset t ON t.id = f.tagset WHERE f.entry_id = ? ORDER BY f.ord",
            listOf(id),
        ) { r -> Form(r.string(0), r.string(1)) }

        val variants = db.query(
            """
            SELECT coalesce(l.text, l.key), coalesce(t.tags, '') FROM lookup l LEFT JOIN tagset t ON t.id = l.tagset
            WHERE l.entry_id = ? AND l.kind = 1
            """.trimIndent(),
            listOf(id),
        ) { r -> Variant(r.string(0), r.string(1)) }
            .filter { it.text != summary.word }
            .distinctBy { it.text }

        val homographs = db.query(
            "SELECT $summaryCols FROM entry e WHERE e.norm = (SELECT norm FROM entry WHERE id = ?) AND e.id != ? ORDER BY e.score DESC",
            listOf(id, id),
        ) { it.summary() }

        val related = rels.filter { it.first == null }.groupBy({ it.second }, { it.third })
            .mapValues { (_, v) -> v.distinctBy { it.word } }

        return EntryDetail(
            summary = summary, altWord = extra.alt, head = extra.head, ipaBrazil = extra.ipaBr, audioFile = extra.audio,
            hyphenation = extra.hyph, etymology = extra.etym, frequencyPerMillion = extra.freq, senses = senses,
            forms = forms, variants = variants, related = related, homographs = homographs,
            sources = listOfNotNull(sources[extra.sourceId]) + sources.values.filter { it.code == "natura" },
        )
    }

    /** Batch-resolve related words to headword/variant entries (best score wins). */
    private fun resolveMany(words: List<String>): Map<String, Long> {
        val byKey = words.distinct().groupBy { PortugueseText.key(it) }
        val out = HashMap<String, Long>()
        for (chunk in byKey.keys.chunked(400)) {
            val rows = db.query(
                """
                SELECT l.key, coalesce(l.text, l.key), e.id FROM lookup l JOIN entry e ON e.id = l.entry_id
                WHERE l.key IN (${chunk.joinToString(",") { "?" }}) AND l.kind <= 1 ORDER BY e.score DESC
                """.trimIndent(),
                chunk,
            ) { r -> Triple(r.string(0), r.string(1), r.long(2)) }
            for ((key, text, eid) in rows) {
                for (w in byKey[key].orEmpty()) {
                    if (w !in out || text == w) out.putIfAbsent(w, eid)
                }
            }
        }
        return out
    }

    /** Resolve a tapped word (synonym chip, derived term) to the best entry id. */
    fun resolve(word: String): Long? =
        searchPortuguese(word, 1).firstOrNull()?.takeIf { it.match.kind != MatchKind.Prefix }?.entry?.id

    /** Stable re-identification across dictionary rebuilds (ids change, word + pos do not). */
    fun find(word: String, pos: String): EntrySummary? = db.query(
        "SELECT $summaryCols FROM entry e WHERE e.norm = ? AND e.word = ? AND e.pos = ? ORDER BY e.score DESC LIMIT 1",
        listOf(PortugueseText.key(word), word, pos),
    ) { it.summary() }.firstOrNull()

    fun meta(): Map<String, String> = db.query("SELECT key, value FROM meta", emptyList()) { it.string(0) to it.string(1) }.toMap()

    fun randomCommonEntry(seed: Long): EntrySummary? = db.query(
        "SELECT $summaryCols FROM entry e WHERE e.rank BETWEEN 200 AND 6000 AND e.variety != 2 AND e.pos != 'proper noun' AND e.flags & 1 = 0 " +
            "ORDER BY (e.id * 2654435761 + ?) % 1000003 LIMIT 1",
        listOf(seed),
    ) { it.summary() }.firstOrNull()

    private fun Row.labelList(i: Int): List<String> =
        stringOrNull(i)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
}
