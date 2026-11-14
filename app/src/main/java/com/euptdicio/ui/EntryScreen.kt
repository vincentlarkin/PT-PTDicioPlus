package com.euptdicio.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.euptdicio.core.EntryDetail
import com.euptdicio.core.EntryFlags
import com.euptdicio.core.Grammar
import com.euptdicio.core.Relation
import com.euptdicio.core.Variety
import kotlinx.coroutines.launch

@Composable
fun EntryPane(state: AppState, entry: EntryDetail, showBack: Boolean, contentPadding: PaddingValues) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(entry.summary.id) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        EntryTopBar(state, entry, showBack)
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 40.dp),
        ) {
            item(key = "head") { Header(state, entry) }
            item(key = "jump") { JumpBar(entry) { key -> scope.launch { list.animateScrollToItem(indexOf(entry, key)) } } }
            item(key = "senses-title") { SectionTitle(if (entry.senses.size == 1) "Meaning" else "Meanings") }
            itemsIndexed(entry.senses, key = { _, s -> "s${s.id}" }) { i, s -> SenseBlock(state, i + 1, s) }
            item(key = "grammar") { GrammarBlock(state, entry) }
            item(key = "variants") { VariantsBlock(entry) }
            item(key = "related") { RelatedBlock(state, entry) }
            item(key = "homographs") { HomographBlock(state, entry) }
            item(key = "etym") {
                entry.etymology?.takeIf { it.isNotBlank() }?.let {
                    SectionTitle("Etymology")
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item(key = "src") { SourcesBlock(entry) }
        }
    }
}

/** Item index of each jump target; must mirror the item order in EntryPane. */
private fun indexOf(entry: EntryDetail, key: String): Int {
    val n = entry.senses.size
    return when (key) {
        "senses" -> 2
        "grammar" -> 3 + n
        "related" -> 5 + n
        "etym" -> 7 + n
        else -> 0
    }
}

@Composable
private fun JumpBar(entry: EntryDetail, onJump: (String) -> Unit) {
    val targets = buildList {
        if (entry.senses.size > 3) add("senses" to "Meanings")
        val hasGrammar = if (entry.summary.pos == "verb") entry.forms.size > 20 else Grammar.nominalForms(entry.forms).isNotEmpty()
        if (hasGrammar && (entry.senses.size > 2 || entry.summary.pos == "verb")) add("grammar" to if (entry.summary.pos == "verb") "Conjugation" else "Forms")
        if (entry.related.values.sumOf { it.size } > 0 && entry.senses.size > 2) add("related" to "Expressions & related")
        if (!entry.etymology.isNullOrBlank() && entry.senses.size > 3) add("etym" to "Etymology")
    }
    if (targets.size < 2) return
    Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.forEach { (key, label) ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onJump(key) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun EntryTopBar(state: AppState, entry: EntryDetail, showBack: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = { state.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { clipboard.setText(AnnotatedString(entry.summary.word)) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy word")
        }
        IconButton(onClick = {
            val text = buildString {
                append(entry.summary.word)
                entry.summary.ipa?.let { append(" $it") }
                append(" — ")
                append(entry.senses.take(3).joinToString("; ") { it.gloss })
            }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        }) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
        val fav = state.isFavorite(entry)
        IconButton(onClick = { state.toggleFavorite(entry) }) {
            Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = "Favourite",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: AppState, entry: EntryDetail) {
    val s = entry.summary
    val c = LocalDicio.current
    Column(Modifier.padding(top = 4.dp)) {
        SelectionContainer { Text(s.word, style = MaterialTheme.typography.displaySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(posFull(s.pos, s.gender), fontStyle = FontStyle.Italic, color = c.faint, style = MaterialTheme.typography.bodyLarge)
            RegionBadge(s.variety)
            if (s.flags and EntryFlags.ARCHAIC != 0) LabelChip("archaic")
            s.rank?.let { r -> if (r <= 10_000) Pill(commonness(r), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                s.ipa?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)) }
                if (state.showBrazilianIpa) entry.ipaBrazil?.takeIf { it != s.ipa }?.let {
                    Text("Brazil $it", style = MaterialTheme.typography.bodyMedium, color = c.faint)
                }
                entry.hyphenation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.faint) }
            }
            entry.audioUrl?.let { url ->
                val audio = state.audio
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { state.playAudio(url) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (audio == AudioState.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Play pronunciation", Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (audio == AudioState.Failed) "Offline?" else "Ouvir", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (entry.altWord != null) {
            Text("Brazilian spelling: ${entry.altWord}", style = MaterialTheme.typography.bodyMedium, color = c.brazil,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (s.variety == Variety.BRAZIL) {
            Text("Used in Brazil, not in Portugal.", style = MaterialTheme.typography.bodyMedium, color = c.brazil,
                modifier = Modifier.padding(top = 6.dp))
        }
        entry.head?.takeIf { it.contains('(') && s.pos != "verb" }?.let {
            Text(it.substringAfter('(', "").removeSuffix(")"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun commonness(rank: Int) = when {
    rank <= 1000 -> "very common"
    rank <= 4000 -> "common"
    else -> "frequent"
}

private fun posFull(pos: String, gender: String?): String {
    val g = when (gender) {
        "m" -> "masculine"; "f" -> "feminine"; "m/f" -> "masculine or feminine"; "m pl" -> "masculine plural"
        "f pl" -> "feminine plural"; null, "" -> null; else -> gender
    }
    return listOfNotNull(pos, g).joinToString(", ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseBlock(state: AppState, n: Int, s: com.euptdicio.core.Sense) {
    val c = LocalDicio.current
    Row(Modifier.padding(vertical = 8.dp)) {
        Text("$n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 24.dp))
        Column(Modifier.weight(1f)) {
            if (s.labels.isNotEmpty() || s.topic != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)) {
                    s.labels.forEach { LabelChip(it) }
                    s.topic?.let { Pill(it.replace('-', ' '), c.faint, MaterialTheme.colorScheme.surfaceContainer) }
                }
            }
            SelectionContainer { Text(s.gloss, style = MaterialTheme.typography.bodyLarge) }
            s.examples.forEach { ExampleBlock(it.pt, it.en) }
            if (s.synonyms.isNotEmpty()) RelationRow(state, "≈", s.synonyms)
            if (s.antonyms.isNotEmpty()) RelationRow(state, "≠", s.antonyms)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationRow(state: AppState, mark: String, rels: List<Relation>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(mark, color = LocalDicio.current.faint, style = MaterialTheme.typography.titleMedium)
        rels.take(14).forEach { r -> WordChip(r.word, r.entryId != null, { r.entryId?.let { state.open(it) } }, r.labels) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrammarBlock(state: AppState, entry: EntryDetail) {
    if (entry.summary.pos == "verb") {
        val table = remember(entry.summary.id) { Grammar.conjugation(entry.forms) } ?: return
        var all by rememberSaveable(entry.summary.id) { mutableStateOf(false) }
        SectionTitle("Conjugation")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            table.gerund?.let { KeyForm("gerúndio", it) }
            table.participle?.let { KeyForm("particípio", it) }
        }
        Column(Modifier.animateContentSize()) {
            val shown = if (all) table.sections else table.sections.filter { it.key in setOf("pres", "pret", "imperf", "fut") }
            shown.forEach { TenseCard(it, state.showBrazilianForms) }
        }
        TextButton(onClick = { all = !all }) {
            Text(if (all) "Fewer tenses" else "All ${table.sections.size} tenses: subjunctive, conditional, commands…")
        }
    } else {
        val forms = remember(entry.summary.id) { Grammar.nominalForms(entry.forms) }
        if (forms.isEmpty()) return
        SectionTitle("Forms")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            forms.forEach { (label, form) -> KeyForm(label, form) }
        }
    }
}

@Composable
private fun KeyForm(label: String, form: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalDicio.current.faint)
        SelectionContainer { Text(form, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun TenseCard(section: Grammar.Section, showBrazil: Boolean) {
    val c = LocalDicio.current
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(section.english, style = MaterialTheme.typography.bodySmall, color = c.faint)
        }
        Spacer(Modifier.height(6.dp))
        section.rows.forEach { row ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(row.person, style = MaterialTheme.typography.bodyMedium, color = c.faint, modifier = Modifier.width(118.dp))
                SelectionContainer { Text(row.forms.joinToString(" / "), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                if (showBrazil && row.brazilOnly.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("BR ${row.brazilOnly.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = c.brazil,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun VariantsBlock(entry: EntryDetail) {
    val shown = entry.variants.filter { !it.kind.contains("misspelling") }.take(8)
    if (shown.isEmpty()) return
    SectionTitle("Other spellings")
    shown.forEach { v ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(v.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 120.dp))
            Spacer(Modifier.width(12.dp))
            Text(variantLabel(v.kind), style = MaterialTheme.typography.bodyMedium, color = LocalDicio.current.faint)
        }
    }
}

private val relatedTitles = linkedMapOf(
    "synonym" to "Synonyms", "antonym" to "Antonyms", "derived" to "Expressions & derived terms",
    "related" to "Related words", "hypernym" to "Broader terms", "hyponym" to "Narrower terms",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedBlock(state: AppState, entry: EntryDetail) {
    for ((kind, title) in relatedTitles) {
        val rels = entry.related[kind].orEmpty()
        if (rels.isEmpty()) continue
        var all by rememberSaveable(entry.summary.id, kind) { mutableStateOf(false) }
        SectionTitle("$title (${rels.size})")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.animateContentSize()) {
            (if (all) rels else rels.take(18)).forEach { r ->
                WordChip(r.word, r.entryId != null, { r.entryId?.let { state.open(it) } ?: state.openWord(r.word) }, r.labels)
            }
        }
        if (rels.size > 18) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${rels.size}") }
    }
}

@Composable
private fun HomographBlock(state: AppState, entry: EntryDetail) {
    if (entry.homographs.isEmpty()) return
    SectionTitle("Also “${entry.summary.word}”")
    entry.homographs.forEach { h ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { state.open(h.id) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(h.word, style = MaterialTheme.typography.titleMedium)
            Text(posLabel(h.pos, h.gender), fontStyle = FontStyle.Italic, color = LocalDicio.current.faint)
            Text(h.firstGloss, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SourcesBlock(entry: EntryDetail) {
    SectionTitle("Sources")
    entry.sources.distinctBy { it.code }.forEach {
        Text("${it.attribution} — ${it.license}", style = MaterialTheme.typography.bodySmall, color = LocalDicio.current.faint,
            modifier = Modifier.padding(vertical = 2.dp))
    }
}
