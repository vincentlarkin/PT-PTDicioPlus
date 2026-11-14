package com.euptdicio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.euptdicio.data.ThemeMode
import com.euptdicio.ui.AppState
import com.euptdicio.ui.DicioTheme
import com.euptdicio.ui.Divider
import com.euptdicio.ui.EntryPane
import com.euptdicio.ui.LocalDicio
import com.euptdicio.ui.SavedPane
import com.euptdicio.ui.SearchField
import com.euptdicio.ui.SearchPane
import com.euptdicio.ui.SectionTitle
import com.euptdicio.ui.Tab

class MainActivity : ComponentActivity() {
    private var appState: AppState? = null
    private var pendingText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingText = intent.lookupText()
        setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val state = remember { AppState(context.applicationContext, scope).also { appState = it; it.start() } }
            LaunchedEffect(Unit) { pendingText?.let { pendingText = null; state.externalLookup(it) } }
            DicioTheme(state.themeMode) { App(state) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.lookupText()?.let { text -> appState?.externalLookup(text) ?: run { pendingText = text } }
    }

    private fun Intent.lookupText(): String? = when (action) {
        Intent.ACTION_PROCESS_TEXT -> getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }
}

@Composable
private fun App(state: AppState) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val dict = state.dictionary
        when {
            state.loadError != null -> Centered("Could not open the dictionary", state.loadError ?: "")
            dict == null -> Installing(state.installProgress)
            else -> Main(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Main(state: AppState) {
    val focus = remember { FocusRequester() }
    BackHandler(enabled = state.stack.isNotEmpty() || state.query.isNotEmpty() || state.tab != Tab.Search) { state.back() }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val width = maxWidth
        val wide = width >= 640.dp
        val entry = state.current
        val listPane: @Composable (PaddingValues) -> Unit = { pad ->
            Column(Modifier.fillMaxSize()) {
                if (state.tab == Tab.Search) {
                    SearchField(state.query, state::onQuery, focus, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                Box(Modifier.weight(1f)) {
                    when (state.tab) {
                        Tab.Search -> SearchPane(state, entry?.summary?.id, pad)
                        Tab.Saved -> SavedPane(state, pad)
                        Tab.About -> AboutPane(state)
                    }
                }
                if (!WindowInsets.isImeVisible) {
                    BottomTabs(state) { if (it == Tab.Search && state.tab == Tab.Search) focus.requestFocus() }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width((width * 0.4f).coerceIn(320.dp, 460.dp)).fillMaxHeight()) { listPane(PaddingValues()) }
                VerticalDivider(color = LocalDicio.current.rule)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(entry, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                        contentKey = { it?.summary?.id }, label = "detail") { e ->
                        if (e != null) EntryPane(state, e, showBack = state.stack.size > 1, PaddingValues())
                        else Centered("EU-PTDicio+", "Pick a word on the left.")
                    }
                }
            }
        } else {
            AnimatedContent(entry, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                contentKey = { it?.summary?.id }, label = "page") { e ->
                if (e != null) EntryPane(state, e, showBack = true, PaddingValues()) else listPane(PaddingValues())
            }
        }
        LaunchedEffect(Unit) { if (state.query.isEmpty() && !wide) runCatching { focus.requestFocus() } }
    }
}

@Composable
private fun BottomTabs(state: AppState, onReselect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        windowInsets = WindowInsets(0), modifier = Modifier.height(64.dp)) {
        listOf(Triple(Tab.Search, "Search", Icons.Rounded.Search), Triple(Tab.Saved, "Saved", Icons.Rounded.Star),
            Triple(Tab.About, "About", Icons.Rounded.Info)).forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = state.tab == tab,
                onClick = { if (state.tab == tab) onReselect(tab); state.tab = tab; state.closeEntries() },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun AboutPane(state: AppState) {
    val meta = remember { state.dictionary?.meta().orEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("EU-PTDicio+", style = MaterialTheme.typography.headlineSmall)
            Text("European Portuguese ⇄ English. Offline, no ads, no tracking.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    val on = state.themeMode == m
                    Text(m.name, modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { state.setTheme(m) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            SectionTitle("Brazilian Portuguese")
            ToggleRow("Show Brazilian conjugation variants", state.showBrazilianForms, state::setBrazilianForms)
            ToggleRow("Show Brazilian pronunciation", state.showBrazilianIpa, state::setBrazilianIpa)
            SectionTitle("This dictionary")
            listOf(
                "Entries" to meta["entries"], "Meanings" to meta["senses"], "Searchable forms" to meta["lookup_rows"],
                "English index" to meta["en_index_rows"], "Built" to meta["built_at_utc"]?.take(10),
            ).forEach { (k, v) -> if (v != null) InfoRow(k, v.toLongOrNull()?.let { "%,d".format(it) } ?: v) }
            SectionTitle("Sources & licences")
            Text(
                "Meanings, pronunciation, conjugations and translations come from Wiktionary (CC BY-SA 4.0 / GFDL) via " +
                    "Wiktextract/Kaikki. European spelling validation and pre-1990 spellings from Projecto Natura " +
                    "(Universidade do Minho, GPL/LGPL/MPL). Frequency from OpenSubtitles word lists by Hermit Dave (CC BY-SA 4.0) " +
                    "and a CC0 European Portuguese web list. Audio streams from Wikimedia Commons / Lingua Libre (CC BY-SA). " +
                    "Full list: docs/SOURCES.md in the project repository.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(v, style = MaterialTheme.typography.bodyMedium)
        }
        Divider()
    }
}

@Composable
private fun Installing(progress: Float?) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("EU-PTDicio+", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        if (progress != null) {
            Text("Preparing the offline dictionary…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
private fun Centered(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
