package com.euptdicio.core

/** Bit flags stored in entry.variety / sense.variety. */
object Variety {
    const val PORTUGAL = 1
    const val BRAZIL = 2
    const val AFRICA = 4
}

/** Bit flags stored in entry.flags. */
object EntryFlags {
    const val ARCHAIC = 1
    const val BRAZILIAN_SPELLING = 2
    const val AO90_VALID = 4
}

enum class MatchKind(val code: Int) {
    Headword(0), Variant(1), Form(2), FormVariant(3), Clitic(10), Prefix(20), English(30), EnglishPrefix(31);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: Form
    }
}

/** Compact row for result lists. */
data class EntrySummary(
    val id: Long,
    val word: String,
    val pos: String,
    val gender: String?,
    val ipa: String?,
    val variety: Int,
    val flags: Int,
    val rank: Int?,
    val score: Int,
    val firstGloss: String,
)

/** Why a result matched, e.g. "falávamos" is "we · imperfect" of falar, or "acção" is a pre-AO90 spelling. */
data class MatchInfo(
    val kind: MatchKind,
    val matchedText: String,
    val detail: String? = null,
    val note: String? = null,
)

data class SearchHit(val entry: EntrySummary, val match: MatchInfo)

data class SearchResults(
    val query: String,
    val portuguese: List<SearchHit>,
    val english: List<SearchHit>,
    /** True when the query looks more like English than Portuguese; the UI shows that section first. */
    val englishFirst: Boolean,
    val tookMicros: Long,
)

data class Sense(
    val id: Long,
    val gloss: String,
    val labels: List<String>,
    val topic: String?,
    val variety: Int,
    val examples: List<Example>,
    val synonyms: List<Relation>,
    val antonyms: List<Relation>,
)

data class Example(val pt: String, val en: String?, val source: String)

data class Relation(val word: String, val labels: List<String>, val entryId: Long?)

data class Form(val form: String, val tags: String)

data class Variant(val text: String, val kind: String)

data class Source(val code: String, val license: String, val attribution: String, val url: String)

data class EntryDetail(
    val summary: EntrySummary,
    val altWord: String?,
    val head: String?,
    val ipaBrazil: String?,
    val audioFile: String?,
    val hyphenation: String?,
    val etymology: String?,
    val frequencyPerMillion: Double,
    val senses: List<Sense>,
    val forms: List<Form>,
    val variants: List<Variant>,
    val related: Map<String, List<Relation>>,  // derived, related, synonym, antonym, hypernym, hyponym (entry level)
    val homographs: List<EntrySummary>,         // same headword, other parts of speech / etymologies
    val sources: List<Source>,
) {
    /** Wikimedia Commons download URL for [audioFile]. */
    val audioUrl: String?
        get() = audioFile?.let { CommonsUrl.forFile(it) }
}

object CommonsUrl {
    fun forFile(name: String): String {
        val file = name.replace(' ', '_')
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(file.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val encoded = java.net.URLEncoder.encode(file, "UTF-8").replace("+", "%20")
        return "https://upload.wikimedia.org/wikipedia/commons/${md5[0]}/${md5.substring(0, 2)}/$encoded"
    }
}
