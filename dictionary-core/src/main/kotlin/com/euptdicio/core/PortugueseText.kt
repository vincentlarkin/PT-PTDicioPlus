package com.euptdicio.core

import java.text.Normalizer

/** Text helpers shared by every lookup. */
object PortugueseText {
    private val combining = Regex("[\\u0300-\\u036f]")
    private val spaces = Regex("\\s+")

    /**
     * Lookup key: lowercase, accents stripped (ç -> c), whitespace collapsed.
     * MUST stay identical to `norm()` in pipeline/common.py.
     */
    fun key(input: String): String {
        var s = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
        s = combining.replace(s, "")
        s = s.replace('’', '\'').replace('‐', '-').replace('–', '-')
        return spaces.replace(s, " ")
    }

    /** Smallest key that is greater than every key starting with [prefix] (for range scans). */
    fun prefixUpperBound(prefix: String): String = prefix + '￿'

    private val objectPronouns = setOf(
        "me", "te", "se", "lhe", "nos", "vos", "lhes", "o", "a", "os", "as",
        "mo", "ma", "mos", "mas", "to", "ta", "tos", "tas", "lho", "lha", "lhos", "lhas",
    )
    private val loForms = setOf("lo", "la", "los", "las")
    private val noForms = setOf("no", "na", "nos", "nas")
    private val mesoclisisEndings = setOf("a", "as", "ei", "emos", "eis", "ao", "ia", "ias", "iamos", "ieis", "iam")

    /**
     * Candidate verb forms hidden behind European Portuguese clitics, as lookup keys.
     *   dá-me -> da          fazê-lo -> fazer       pô-lo -> por        fá-lo -> faz
     *   põe-no -> poe        vamo-nos -> vamos      dar-me-á -> dara    di-lo-ia -> diria
     */
    fun cliticCandidates(input: String): List<String> {
        val k = key(input)
        if ('-' !in k || ' ' in k) return emptyList()
        val parts = k.split('-').filter { it.isNotEmpty() }
        if (parts.size < 2) return emptyList()
        val verb = parts[0]
        val out = LinkedHashSet<String>()
        if (parts.size == 3 && parts[2] in mesoclisisEndings && (parts[1] in objectPronouns || parts[1] in loForms)) {
            out += verb + parts[2]                      // dar-me-á -> dará
            if (parts[1] in loForms) out += verb + "r" + parts[2]  // fá-lo-ei -> farei
        }
        val pronouns = parts.drop(1)
        if (pronouns.all { it in objectPronouns || it in loForms || it in noForms }) {
            when (pronouns.first()) {
                in loForms -> {
                    out += verb + "r"   // fazê-lo -> fazer, pô-lo -> pôr, parti-lo -> partir
                    out += verb + "s"   // comemo-lo -> comemos, fi-lo? no: fi-lo -> fiz below
                    out += verb + "z"   // fá-lo -> faz, di-lo -> diz, fi-lo -> fiz
                    out += verb + "is"  // qui-lo -> quis (key "quis")
                }
                "nos" -> {
                    out += verb + "s"   // vamo-nos -> vamos
                    out += verb
                }
                else -> out += verb     // dá-me, põe-no, dão-no, levanta-te
            }
        }
        out.remove(k)
        return out.toList()
    }
}
