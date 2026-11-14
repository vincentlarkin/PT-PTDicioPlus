package com.euptdicio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.euptdicio.core.Grammar
import com.euptdicio.core.MatchKind
import com.euptdicio.core.SearchHit
import com.euptdicio.core.Variety

private val posShort = mapOf(
    "noun" to "n.", "verb" to "v.", "adjective" to "adj.", "adverb" to "adv.", "proper noun" to "prop. n.",
    "preposition" to "prep.", "conjunction" to "conj.", "pronoun" to "pron.", "interjection" to "interj.",
    "determiner" to "det.", "numeral" to "num.", "contraction" to "contr.", "phrase" to "phr.", "article" to "art.",
)

fun posLabel(pos: String, gender: String?): String =
    listOfNotNull(posShort[pos] ?: pos, gender?.takeIf { it.isNotBlank() }).joinToString(" ")

@Composable
fun RegionBadge(variety: Int, modifier: Modifier = Modifier) {
    val c = LocalDicio.current
    val (text, fg, bg) = when {
        variety and Variety.PORTUGAL != 0 -> Triple("PT", c.portugal, c.portugalBg)
        variety and Variety.BRAZIL != 0 -> Triple("BR", c.brazil, c.brazilBg)
        variety and Variety.AFRICA != 0 -> Triple("AF", c.africa, c.africaBg)
        else -> return
    }
    Pill(text, fg, bg, modifier)
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Usage label like "colloquial" or "Brazil"; regions get their badge colours. */
@Composable
fun LabelChip(raw: String) {
    val c = LocalDicio.current
    val text = Grammar.label(raw)
    val (fg, bg) = when (raw) {
        "Portugal", "European-Portuguese", "Azores", "Madeira", "Lisbon", "Porto", "Alentejo", "Minho" -> c.portugal to c.portugalBg
        "Brazil", "Brazilian-Portuguese" -> c.brazil to c.brazilBg
        "Angola", "Mozambique", "Cape-Verde", "Guinea-Bissau", "Sao-Tome-and-Principe" -> c.africa to c.africaBg
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Pill(text, fg, bg)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Português ou English…", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange(""); focusRequester.requestFocus() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear")
            }
        }
    }
}

/** One compact result: headword, part of speech, why it matched, first meaning. */
@Composable
fun ResultRow(hit: SearchHit, selected: Boolean, onClick: () -> Unit) {
    val e = hit.entry
    val c = LocalDicio.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.word, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Text(posLabel(e.pos, e.gender), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                color = c.faint)
            RegionBadge(e.variety)
            hit.match.note?.takeIf { it != "Portugal" && e.variety == 0 }?.let { note ->
                if (note == "Brazil") Pill("BR", c.brazil, c.brazilBg) else LabelChip(note)
            }
        }
        matchLine(hit)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        if (e.firstGloss.isNotEmpty()) {
            Text(e.firstGloss, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun matchLine(hit: SearchHit): String? {
    val m = hit.match
    return when (m.kind) {
        MatchKind.Form -> "${m.matchedText} → ${Grammar.describe(m.detail) ?: "form"}"
        MatchKind.FormVariant -> "${m.matchedText} → ${Grammar.describe(m.detail) ?: "old spelling of a form"}"
        MatchKind.Variant -> "${m.matchedText} → ${m.detail?.let { variantLabel(it) } ?: "variant"}"
        MatchKind.Clitic -> "${m.note} → ${m.matchedText}${m.detail?.let { " · " + Grammar.describe(it) } ?: ""} + pronoun"
        MatchKind.English -> m.detail?.let { "${m.matchedText} ($it)" }
            ?: if (hit.entry.firstGloss.lowercase().contains(m.matchedText)) null else "“${m.matchedText}”"
        MatchKind.EnglishPrefix -> if (hit.entry.firstGloss.lowercase().contains(m.matchedText)) null else "“${m.matchedText}”"
        else -> null
    }
}

fun variantLabel(kind: String): String = when {
    kind.startsWith("pre-AO90") -> "spelling before the 1990 reform"
    kind == "Brazil spelling" -> "Brazilian spelling"
    kind == "Portugal spelling" -> "European spelling"
    else -> kind
}

@Composable
fun Divider() = HorizontalDivider(color = LocalDicio.current.rule, thickness = 1.dp)

@Composable
fun ExampleBlock(pt: String, en: String?) {
    val c = LocalDicio.current
    Row(Modifier.padding(top = 6.dp)) {
        Box(Modifier.width(3.dp).height(if (en != null) 44.dp else 22.dp).background(c.rule))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(buildAnnotatedString { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(pt) } },
                style = MaterialTheme.typography.bodyMedium)
            if (en != null) Text(en, style = MaterialTheme.typography.bodySmall, color = c.faint)
        }
    }
}

@Composable
fun WordChip(text: String, linked: Boolean, onClick: () -> Unit, labels: List<String> = emptyList()) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (linked) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainer)
            .clickable(enabled = linked, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (linked) FontWeight.Medium else FontWeight.Normal,
            color = if (linked) cs.onPrimaryContainer else cs.onSurfaceVariant)
        labels.firstOrNull { it in setOf("Brazil", "Portugal", "Angola", "Mozambique", "Madeira", "Azores") }?.let { LabelChip(it) }
    }
}

@Composable
fun Dot() = Box(Modifier.size(3.dp).clip(RoundedCornerShape(2.dp)).background(LocalDicio.current.faint))
