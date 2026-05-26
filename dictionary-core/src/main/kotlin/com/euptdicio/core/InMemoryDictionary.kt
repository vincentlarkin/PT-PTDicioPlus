package com.euptdicio.core

class InMemoryDictionary(entries: List<DictionaryEntry>) {
    private val entries = entries
    private val lemmaIndex = entries.associateBy { PortugueseNormalizer.normalizeForLookup(it.lemma) }
    private val accentlessLemmaIndex = entries.associateBy { PortugueseNormalizer.stripAccents(it.lemma) }
    private val formIndex = buildMap {
        for (entry in entries) {
            for (form in entry.forms) {
                put(PortugueseNormalizer.normalizeForLookup(form.text), entry)
            }
        }
    }
    private val accentlessFormIndex = buildMap {
        for (entry in entries) {
            for (form in entry.forms) {
                put(PortugueseNormalizer.stripAccents(form.text), entry)
            }
        }
    }

    fun lookup(
        query: String,
        limit: Int = 12,
        direction: LookupDirection = LookupDirection.PortugueseToEnglish,
    ): List<LookupResult> {
        return when (direction) {
            LookupDirection.PortugueseToEnglish -> lookupPortuguese(query, limit)
            LookupDirection.EnglishToPortuguese -> lookupEnglish(query, limit)
        }
    }

    private fun lookupPortuguese(query: String, limit: Int): List<LookupResult> {
        val normalized = PortugueseNormalizer.normalizeForLookup(query)
        if (normalized.isBlank()) return emptyList()

        val accentless = PortugueseNormalizer.stripAccents(normalized)
        val matches = mutableMapOf<String, LookupResult>()

        lemmaIndex[normalized]?.let { entry ->
            matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = entry.lemma, matchType = MatchType.ExactLemma, score = 1000))
        }
        formIndex[normalized]?.let { entry ->
            matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = normalized, matchType = MatchType.InflectedForm, score = 900))
        }
        accentlessLemmaIndex[accentless]?.let { entry ->
            matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = entry.lemma, matchType = MatchType.AccentInsensitive, score = 760))
        }
        accentlessFormIndex[accentless]?.let { entry ->
            matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = normalized, matchType = MatchType.AccentInsensitive, score = 720))
        }
        for (candidate in PortugueseNormalizer.cliticCandidates(normalized)) {
            lemmaIndex[candidate]?.let { entry ->
                matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = normalized, matchType = MatchType.InflectedForm, score = 860))
            }
        }

        for (entry in entries) {
            val lemma = PortugueseNormalizer.normalizeForLookup(entry.lemma)
            val forms = entry.forms.map { PortugueseNormalizer.normalizeForLookup(it.text) }
            if (lemma.startsWith(normalized) || forms.any { it.startsWith(normalized) }) {
                matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = entry.lemma, matchType = MatchType.Prefix, score = 520))
            } else {
                val accentlessLemma = PortugueseNormalizer.stripAccents(entry.lemma)
                if (accentlessLemma.startsWith(accentless)) {
                    matches.putBest(entry.lemma, LookupResult(entry = entry, matchedForm = entry.lemma, matchType = MatchType.Prefix, score = 480))
                }
            }
        }

        return matches.values
            .sortedWith(compareByDescending<LookupResult> { it.score }.thenBy { it.entry.lemma })
            .take(limit)
    }

    private fun lookupEnglish(query: String, limit: Int): List<LookupResult> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()

        return entries
            .mapNotNull { entry ->
                val exact = entry.meanings.firstOrNull { it.lowercase() == normalized }
                val prefix = entry.meanings.firstOrNull { meaning ->
                    meaning.lowercase().startsWith(normalized) ||
                        meaning.lowercase().contains(" $normalized")
                }
                when {
                    exact != null -> LookupResult(entry = entry, matchedForm = exact, matchType = MatchType.EnglishMeaning, score = 840)
                    prefix != null -> LookupResult(entry = entry, matchedForm = prefix, matchType = MatchType.EnglishMeaning, score = 620)
                    else -> null
                }
            }
            .sortedWith(compareByDescending<LookupResult> { it.score }.thenBy { it.entry.lemma })
            .take(limit)
    }

    private fun MutableMap<String, LookupResult>.putBest(key: String, candidate: LookupResult) {
        val existing = this[key]
        if (existing == null || candidate.score > existing.score) {
            this[key] = candidate
        }
    }
}
