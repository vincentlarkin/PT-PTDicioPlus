package com.euptdicio.core

import java.text.Normalizer
import java.util.Locale

object PortugueseNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val punctuationToSpace = Regex("[_\\s]+")

    fun normalizeForLookup(input: String): String {
        return input
            .trim()
            .lowercase(Locale.forLanguageTag("pt-PT"))
            .replace('’', '\'')
            .replace(punctuationToSpace, " ")
    }

    fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(normalizeForLookup(input), Normalizer.Form.NFD)
        return combiningMarks.replace(normalized, "")
    }

    fun cliticCandidates(input: String): List<String> {
        val normalized = normalizeForLookup(input)
        if ('-' !in normalized) return emptyList()

        val parts = normalized.split('-').filter { it.isNotBlank() }
        if (parts.size < 2) return emptyList()

        val first = parts.first()
        val last = parts.last()
        return buildList {
            add(first)
            when {
                first.endsWith("á") -> add(first.dropLast(1) + "ar")
                first.endsWith("ê") -> add(first.dropLast(1) + "er")
                first.endsWith("i") && last in nasalObjectPronouns -> add(first.dropLast(1) + "er")
                first.endsWith("õe") -> add(first.dropLast(2) + "or")
                first.endsWith("ão") -> add(first.dropLast(2) + "ar")
            }
        }.distinct()
    }

    private val nasalObjectPronouns = setOf("no", "na", "nos", "nas")
}

