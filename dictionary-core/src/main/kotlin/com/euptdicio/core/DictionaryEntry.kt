package com.euptdicio.core

data class DictionaryEntry(
    val lemma: String,
    val partOfSpeech: PartOfSpeech,
    val meanings: List<String>,
    val forms: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val source: String,
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
