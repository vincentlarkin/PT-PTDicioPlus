package com.euptdicio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryDictionaryTest {
    private val dictionary = InMemoryDictionary(SampleEntries.europeanPortuguese)

    @Test
    fun `maps inflected verb form to lemma`() {
        val result = dictionary.lookup("falávamos").first()

        assertEquals("falar", result.entry.lemma)
        assertEquals(MatchType.InflectedForm, result.matchType)
    }

    @Test
    fun `finds accent insensitive lemma`() {
        val result = dictionary.lookup("cao").first()

        assertEquals("cão", result.entry.lemma)
        assertEquals(MatchType.AccentInsensitive, result.matchType)
    }

    @Test
    fun `handles common clitic surface form`() {
        val result = dictionary.lookup("fazê-lo").first()

        assertEquals("fazer", result.entry.lemma)
    }

    @Test
    fun `returns prefix suggestions`() {
        val results = dictionary.lookup("fa")

        assertTrue(results.any { it.entry.lemma == "falar" })
        assertTrue(results.any { it.entry.lemma == "fazer" })
    }

    @Test
    fun `finds Portuguese entry from English meaning`() {
        val result = dictionary.lookup(
            query = "dog",
            direction = LookupDirection.EnglishToPortuguese,
        ).first()

        assertEquals("cão", result.entry.lemma)
        assertEquals(MatchType.EnglishMeaning, result.matchType)
    }
}
