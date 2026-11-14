package com.euptdicio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortugueseTextTest {
    @Test
    fun keyStripsAccentsAndCase() {
        assertEquals("acao", PortugueseText.key("  Ação "))
        assertEquals("pequeno-almoco", PortugueseText.key("Pequeno-Almoço"))
        assertEquals("nao me da", PortugueseText.key("não  me   dá"))
    }

    @Test
    fun clitics() {
        fun has(input: String, want: String) =
            assertTrue(want in PortugueseText.cliticCandidates(input), "$input -> ${PortugueseText.cliticCandidates(input)}")
        has("dá-me", "da")
        has("fazê-lo", "fazer")
        has("pô-lo", "por")
        has("fá-lo", "faz")
        has("põe-no", "poe")
        has("vamo-nos", "vamos")
        has("dar-me-á", "dara")
        has("dir-lhe-ia", "diria")
        has("deu-lho", "deu")
    }

    @Test
    fun grammarLabels() {
        assertEquals("nós (we) · imperfect (was doing / used to)", Grammar.describe("first-person imperfect indicative plural"))
        assertEquals("feminine plural", Grammar.describe("feminine plural"))
    }

    @Test
    fun conjugationPrefersEuropeanForms() {
        val p = Grammar.conjugation(listOf(
            Form("falamos", "Brazil first-person indicative plural preterite"),
            Form("falámos", "first-person indicative plural preterite"),
            Form("falei", "first-person indicative preterite singular"),
        ))!!
        val pret = p.sections.single { it.key == "pret" }
        assertEquals(listOf("falámos"), pret.rows.single { it.person == "nós" }.forms)
        assertEquals(listOf("falamos"), pret.rows.single { it.person == "nós" }.brazilOnly)
    }
}
