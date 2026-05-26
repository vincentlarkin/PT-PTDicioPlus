package com.euptdicio.core

object SampleEntries {
    val europeanPortuguese = listOf(
        DictionaryEntry(
            lemma = "falar",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to speak", "to talk"),
            forms = forms("falo", "falas", "fala", "falamos", "falavam", "falávamos", "falarei"),
            examples = listOf(DictionaryExample("Falamos português em casa.", "We speak Portuguese at home.")),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "fazer",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to do", "to make"),
            forms = forms("faço", "fazes", "faz", "fazemos", "fazia", "fiz", "feito", "fazê-lo"),
            examples = listOf(DictionaryExample("Faço o jantar hoje.", "I am making dinner today.")),
            labels = listOf("irregular", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "pôr",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to put", "to place"),
            forms = forms("ponho", "pões", "põe", "pomos", "punha", "pus", "posto", "põe-no"),
            labels = listOf("irregular", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "cão",
            partOfSpeech = PartOfSpeech.Noun,
            meanings = listOf("dog"),
            forms = forms("cães"),
            labels = listOf("masculine", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "bom",
            partOfSpeech = PartOfSpeech.Adjective,
            meanings = listOf("good"),
            forms = forms("boa", "bons", "boas"),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "obrigado",
            partOfSpeech = PartOfSpeech.Phrase,
            meanings = listOf("thank you"),
            forms = forms("obrigada"),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
    )

    private fun forms(vararg values: String): List<DictionaryForm> {
        return values.map(::DictionaryForm)
    }
}
