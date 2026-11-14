package com.euptdicio.core

/** Turns Wiktionary grammar tags into short labels and conjugation tables an English speaker can read. */
object Grammar {
    private val personOrder = listOf("1s", "2s", "3s", "1p", "2p", "3p")
    val personLabels = mapOf(
        "1s" to "eu", "2s" to "tu", "3s" to "ele/ela/você",
        "1p" to "nós", "2p" to "vós", "3p" to "eles/elas/vocês",
    )
    private val personEnglish = mapOf(
        "1s" to "I", "2s" to "you", "3s" to "he/she/you", "1p" to "we", "2p" to "you all", "3p" to "they",
    )

    private fun person(tags: Set<String>): String? {
        val p = when {
            "first-person" in tags -> "1"
            "second-person" in tags -> "2"
            "third-person" in tags -> "3"
            else -> return null
        }
        val n = when {
            "plural" in tags -> "p"
            "singular" in tags -> "s"
            else -> return null
        }
        return p + n
    }

    /** Ordered (key, title, english) for verb tense groups. */
    private val tenses = listOf(
        Triple("pres", "Presente", "present"),
        Triple("pret", "Pretérito perfeito", "preterite (simple past)"),
        Triple("imperf", "Pretérito imperfeito", "imperfect (was doing / used to)"),
        Triple("fut", "Futuro", "future"),
        Triple("cond", "Condicional", "conditional (would)"),
        Triple("plup", "Mais-que-perfeito", "pluperfect (had done)"),
        Triple("spres", "Conjuntivo presente", "present subjunctive"),
        Triple("simperf", "Conjuntivo imperfeito", "imperfect subjunctive"),
        Triple("sfut", "Conjuntivo futuro", "future subjunctive"),
        Triple("imp", "Imperativo", "commands"),
        Triple("impneg", "Imperativo negativo", "negative commands"),
        Triple("pinf", "Infinitivo pessoal", "personal infinitive"),
    )

    private fun tense(tags: Set<String>): String? = when {
        "imperative" in tags && "negative" in tags -> "impneg"
        "imperative" in tags -> "imp"
        "subjunctive" in tags && "present" in tags -> "spres"
        "subjunctive" in tags && "imperfect" in tags -> "simperf"
        "subjunctive" in tags && "future" in tags -> "sfut"
        "conditional" in tags -> "cond"
        "infinitive" in tags && "impersonal" !in tags && person(tags) != null -> "pinf"
        "pluperfect" in tags -> "plup"
        "preterite" in tags -> "pret"
        "imperfect" in tags -> "imperf"
        "future" in tags -> "fut"
        "present" in tags -> "pres"
        else -> null
    }

    /** "first-person plural imperfect indicative" -> "nós (we) · imperfect". */
    fun describe(tagText: String?): String? {
        if (tagText.isNullOrBlank()) return null
        val parts = tagText.split(" · ")
        return parts.joinToString(" · ") { describeOne(it) }
    }

    private fun describeOne(tagText: String): String {
        val tags = tagText.split(' ', ',').filter { it.isNotBlank() }.toSet()
        if (tags.isEmpty()) return tagText
        val bits = mutableListOf<String>()
        if ("pre-AO90" in tags || tagText.startsWith("pre-AO90")) bits += "pre-1990 spelling"
        val p = person(tags)
        val t = tense(tags)
        if (p != null && t != null) {
            bits += "${personLabels[p]} (${personEnglish[p]})"
            bits += tenses.first { it.first == t }.third
        } else {
            when {
                "gerund" in tags -> bits += "gerund (-ing)"
                "participle" in tags -> bits += listOfNotNull(
                    "past participle",
                    if ("feminine" in tags) "feminine" else null,
                    if ("plural" in tags) "plural" else null,
                ).joinToString(" ")
                "infinitive" in tags -> bits += "infinitive"
                else -> {
                    val nominal = listOf("feminine", "masculine", "plural", "singular", "diminutive", "augmentative",
                        "superlative", "comparative", "demonym").filter { it in tags }
                    if (nominal.isNotEmpty()) bits += nominal.joinToString(" ")
                }
            }
        }
        if ("Brazil" in tags) bits += "Brazil"
        if ("Portugal" in tags) bits += "Portugal"
        return bits.joinToString(" · ").ifEmpty { tagText }
    }

    data class Row(val person: String, val forms: List<String>, val brazilOnly: List<String>)
    data class Section(val key: String, val title: String, val english: String, val rows: List<Row>)
    data class Paradigm(
        val infinitive: String?,
        val gerund: String?,
        val participle: String?,
        val sections: List<Section>,
    )

    /** Builds a conjugation table, preferring European forms (falámos) and keeping Brazilian ones flagged. */
    fun conjugation(forms: List<Form>): Paradigm? {
        val cells = LinkedHashMap<Pair<String, String>, Pair<MutableList<String>, MutableList<String>>>()
        var gerund: String? = null
        var participle: String? = null
        var infinitive: String? = null
        for (f in forms) {
            val tags = f.tags.split(' ').toSet()
            when {
                "gerund" in tags -> gerund = gerund ?: f.form
                "participle" in tags && "past" in tags && "feminine" !in tags && "plural" !in tags -> participle = participle ?: f.form
                "impersonal" in tags && "infinitive" in tags -> infinitive = infinitive ?: f.form
            }
            val p = person(tags) ?: continue
            val t = tense(tags) ?: continue
            val (pt, br) = cells.getOrPut(t to p) { mutableListOf<String>() to mutableListOf() }
            val target = if ("Brazil" in tags) br else pt
            if (f.form !in target) target += f.form
        }
        if (cells.isEmpty()) return null
        val sections = tenses.mapNotNull { (key, title, english) ->
            val rows = personOrder.mapNotNull { p ->
                val (pt, br) = cells[key to p] ?: return@mapNotNull null
                Row(personLabels.getValue(p), pt.ifEmpty { br }, if (pt.isEmpty()) emptyList() else br - pt.toSet())
            }
            if (rows.isEmpty()) null else Section(key, title, english, rows)
        }
        return Paradigm(infinitive, gerund, participle, sections)
    }

    /** Non-verb forms worth a chip: plural, feminine, feminine plural, diminutive... */
    fun nominalForms(forms: List<Form>): List<Pair<String, String>> =
        forms.filter { f ->
            val t = f.tags.split(' ').toSet()
            person(t) == null && tense(t) == null && "gerund" !in t && "participle" !in t && "infinitive" !in t &&
                "canonical" !in t && "abbreviation" !in t
        }.map { describeOne(it.tags) to it.form }.distinct()

    private val labelText = mapOf(
        "Portugal" to "Portugal", "European-Portuguese" to "Portugal", "Brazil" to "Brazil",
        "Brazilian-Portuguese" to "Brazil", "Cape-Verde" to "Cape Verde", "Guinea-Bissau" to "Guinea-Bissau",
        "Sao-Tome-and-Principe" to "São Tomé", "East-Timor" to "Timor", "figuratively" to "figurative",
        "transitive" to "transitive", "intransitive" to "intransitive", "pronominal" to "pronominal",
        "uncountable" to "uncountable", "Internet" to "internet",
    )

    fun label(raw: String): String = labelText[raw] ?: raw.replace('-', ' ')
}
