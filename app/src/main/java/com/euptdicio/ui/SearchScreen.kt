package com.euptdicio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euptdicio.core.SearchHit
import com.euptdicio.data.SavedWord

@Composable
fun SearchPane(state: AppState, selectedId: Long?, contentPadding: PaddingValues) {
    val results = state.results
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(results?.query) { list.scrollToItem(0) }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        when {
            state.query.isBlank() -> home(state)
            results == null -> Unit
            results.portuguese.isEmpty() && results.english.isEmpty() -> item {
                Empty("Nothing found for “${state.query}”.",
                    "Try fewer letters, or the word without accents — both directions are searched.")
            }
            else -> {
                val pt = results.portuguese
                val en = results.english
                val sections = if (results.englishFirst) listOf("English → Português" to en, "Português" to pt)
                else listOf("Português" to pt, "English → Português" to en)
                for ((title, hits) in sections) {
                    if (hits.isEmpty()) continue
                    item(key = "h-$title") { SectionTitle(title, Modifier.padding(start = 14.dp)) }
                    items(hits, key = { "$title-${it.entry.id}" }) { hit: SearchHit ->
                        ResultRow(hit, selected = hit.entry.id == selectedId) { keyboard?.hide(); state.open(hit.entry.id) }
                    }
                }
                item {
                    Text("${results.tookMicros / 1000.0} ms", style = MaterialTheme.typography.labelSmall,
                        color = LocalDicio.current.faint, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

private fun LazyListScope.home(state: AppState) {
    state.wordOfDay?.let { w ->
        item(key = "wotd") {
            Column(
                Modifier
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .clickable { state.open(w.id) }
                    .padding(20.dp),
            ) {
                Text("PALAVRA DO DIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(w.word, style = MaterialTheme.typography.headlineSmall)
                    Text(posLabel(w.pos, w.gender), fontStyle = FontStyle.Italic, color = LocalDicio.current.faint,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                w.ipa?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalDicio.current.faint) }
                Spacer(Modifier.height(4.dp))
                Text(w.firstGloss, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (state.history.isNotEmpty()) {
        item(key = "recent-h") { SectionTitle("Recent", Modifier.padding(start = 14.dp)) }
        items(state.history.take(25), key = { "r-${it.word}-${it.pos}" }) { SavedRow(it) { state.openSaved(it) } }
    } else {
        item(key = "hint") {
            Column(Modifier.padding(18.dp)) {
                Text("Type a word in either language.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "falávamos → falar (we were speaking)",
                    "dá-me, fazê-lo, dar-me-á → the verb behind the pronouns",
                    "acção → ação (spelling before the 1990 reform)",
                    "bus → autocarro, not ônibus",
                    "Select text in any app → “Look up in EU-PTDicio+”",
                ).forEach {
                    Text("·  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
fun SavedRow(w: SavedWord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(w.word, style = MaterialTheme.typography.titleMedium)
        Text(w.gloss, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SavedPane(state: AppState, contentPadding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item { SectionTitle("Favourites (${state.favorites.size})", Modifier.padding(start = 14.dp)) }
        if (state.favorites.isEmpty()) item { Empty("No favourites yet", "Tap ☆ on any entry to keep it here.") }
        items(state.favorites, key = { "f-${it.word}-${it.pos}" }) { SavedRow(it) { state.openSaved(it) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("History", Modifier.padding(start = 14.dp).weight(1f))
                if (state.history.isNotEmpty()) TextButton(onClick = state::clearHistory) { Text("Clear") }
            }
        }
        items(state.history, key = { "h-${it.word}-${it.pos}" }) { SavedRow(it) { state.openSaved(it) } }
    }
}
