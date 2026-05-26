package com.euptdicio.core

data class DictionaryEntry(
    val lemma: String,
    val partOfSpeech: PartOfSpeech,
    val meanings: List<String>,
    val forms: List<DictionaryForm> = emptyList(),
    val examples: List<DictionaryExample> = emptyList(),
    val labels: List<String> = emptyList(),
    val source: String,
)

data class DictionaryForm(
    val text: String,
    val tags: List<String> = emptyList(),
)

data class DictionaryExample(
    val text: String,
    val translation: String? = null,
)

enum class PartOfSpeech(val displayName: String) {
    Noun("noun"),
    Verb("verb"),
    Adjective("adjective"),
    Adverb("adverb"),
    Phrase("phrase"),
}

data class LookupResult(
    val entry: DictionaryEntry,
    val matchedForm: String,
    val matchType: MatchType,
    val score: Int,
)

enum class LookupDirection {
    PortugueseToEnglish,
    EnglishToPortuguese,
}

enum class MatchType {
    ExactSurface,
    ExactLemma,
    InflectedForm,
    AccentInsensitive,
    Prefix,
    EnglishMeaning,
}
