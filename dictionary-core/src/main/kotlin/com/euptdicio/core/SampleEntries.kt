package com.euptdicio.core

object SampleEntries {
    val europeanPortuguese = listOf(
        DictionaryEntry(
            lemma = "falar",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to speak", "to talk"),
            forms = listOf("falo", "falas", "fala", "falamos", "falavam", "falávamos", "falarei"),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "fazer",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to do", "to make"),
            forms = listOf("faço", "fazes", "faz", "fazemos", "fazia", "fiz", "feito", "fazê-lo"),
            labels = listOf("irregular", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "pôr",
            partOfSpeech = PartOfSpeech.Verb,
            meanings = listOf("to put", "to place"),
            forms = listOf("ponho", "pões", "põe", "pomos", "punha", "pus", "posto", "põe-no"),
            labels = listOf("irregular", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "cão",
            partOfSpeech = PartOfSpeech.Noun,
            meanings = listOf("dog"),
            forms = listOf("cães"),
            labels = listOf("masculine", "PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "bom",
            partOfSpeech = PartOfSpeech.Adjective,
            meanings = listOf("good"),
            forms = listOf("boa", "bons", "boas"),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
        DictionaryEntry(
            lemma = "obrigado",
            partOfSpeech = PartOfSpeech.Phrase,
            meanings = listOf("thank you"),
            forms = listOf("obrigada"),
            labels = listOf("PT-PT"),
            source = "seed",
        ),
    )
}

