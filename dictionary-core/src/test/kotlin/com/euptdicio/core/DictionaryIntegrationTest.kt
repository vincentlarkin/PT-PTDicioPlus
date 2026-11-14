package com.euptdicio.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Runs against data/build/dicio.sqlite; skipped when the dictionary has not been built. */
class DictionaryIntegrationTest {
    private val path = System.getProperty("dicio.db")?.takeIf { File(it).exists() }

    private fun withDict(block: (Dictionary) -> Unit) {
        if (path == null) {
            println("SKIP: build the dictionary first (python3 -I tools/build_dictionary.py)")
            return
        }
        JdbcDb(path).use { block(Dictionary(it)) }
    }

    private fun Dictionary.pt(q: String) = searchPortuguese(q, 6).map { it.entry.word }
    private fun Dictionary.en(q: String) = searchEnglish(q, 6).map { it.entry.word }

    private fun expect(got: List<String>, want: String, within: Int, q: String) =
        assertTrue(want in got.take(within), "'$q' expected $want in top $within, got $got")

    @Test
    fun portugueseLookups() = withDict { d ->
        listOf(
            "falávamos" to "falar", "cães" to "cão", "acção" to "ação", "óptimo" to "ótimo", "ponho" to "pôr",
            "ônibus" to "ónibus", "fizeram" to "fazer", "director" to "diretor", "fixe" to "fixe",
            "dá-me" to "dar", "fazê-lo" to "fazer", "dar-me-á" to "dar", "põe-no" to "pôr", "acções" to "ação",
        ).forEach { (q, want) -> expect(d.pt(q), want, 2, q) }
    }

    @Test
    fun englishLookups() = withDict { d ->
        listOf(
            "bus" to "autocarro", "breakfast" to "pequeno-almoço", "train" to "comboio", "mobile phone" to "telemóvel",
            "dog" to "cão", "went" to "ir", "to speak" to "falar", "ice cream" to "gelado", "house" to "casa",
        ).forEach { (q, want) -> expect(d.en(q), want, 2, q) }
    }

    @Test
    fun prefixAndDirection() = withDict { d ->
        assertTrue(d.suggest("autoc").any { it.word == "autocarro" })
        assertTrue(d.search("breakfast").englishFirst)
        assertTrue(!d.search("casa").englishFirst)
    }

    @Test
    fun entryDetailIsComplete() = withDict { d ->
        val e = d.entry(d.searchPortuguese("falar", 1).first().entry.id)!!
        assertTrue(e.senses.isNotEmpty() && e.summary.ipa != null)
        val table = Grammar.conjugation(e.forms)!!
        assertTrue(table.sections.size >= 10, "sections ${table.sections.map { it.key }}")
        val cao = d.entry(d.searchPortuguese("cão", 1).first().entry.id)!!
        assertTrue(cao.senses.first().synonyms.any { it.word == "cachorro" && it.entryId != null })
    }

    @Test
    fun searchIsFast() = withDict { d ->
        val queries = listOf("a", "ca", "casa", "falávamos", "bus", "breakfast", "dá-me", "xyzzy", "pequeno", "the")
        queries.forEach { d.search(it) } // warm up
        val times = queries.flatMap { q -> (1..5).map { d.search(q).tookMicros } }.sorted()
        val p95 = times[(times.size * 0.95).toInt() - 1]
        println("search p95 = ${p95}us (JVM, desktop), max = ${times.last()}us")
        assertTrue(p95 < 30_000, "p95 ${p95}us")
    }
}
