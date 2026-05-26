package com.euptdicio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euptdicio.core.DictionaryForm
import com.euptdicio.core.LookupDirection
import com.euptdicio.core.LookupResult
import com.euptdicio.core.MatchType
import com.euptdicio.core.PartOfSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EUPTDicioTheme {
                DictionaryApp()
            }
        }
    }
}

@Composable
private fun EUPTDicioTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF176B5D),
        onPrimary = Color.White,
        secondary = Color(0xFFD94C38),
        background = Color(0xFFFBF8F1),
        surface = Color(0xFFFFFCF6),
        surfaceVariant = Color(0xFFE9E2D6),
        onSurface = Color(0xFF1E2422),
    )

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun DictionaryApp() {
    val context = LocalContext.current
    val dictionary = remember { DictionaryRepository(context.applicationContext) }
    var directionName by rememberSaveable { mutableStateOf(LookupDirection.PortugueseToEnglish.name) }
    var portugueseQuery by rememberSaveable { mutableStateOf("") }
    var englishQuery by rememberSaveable { mutableStateOf("") }
    var sortModeName by rememberSaveable { mutableStateOf(SortMode.Popularity.name) }
    var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
    var selectedResult by remember { mutableStateOf<LookupResult?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var debugStatus by remember { mutableStateOf<DictionaryDebugStatus?>(null) }
    var debugRefreshKey by remember { mutableStateOf(0) }
    val direction = LookupDirection.valueOf(directionName)
    val sortMode = SortMode.valueOf(sortModeName)
    val query = when (direction) {
        LookupDirection.PortugueseToEnglish -> portugueseQuery
        LookupDirection.EnglishToPortuguese -> englishQuery
    }

    LaunchedEffect(query, direction, sortMode) {
        selectedResult = null
        if (query.isBlank()) {
            results = emptyList()
            lookupError = null
            isSearching = false
        } else {
            isSearching = true
            lookupError = null
            try {
                val lookup = withContext(Dispatchers.IO) {
                    dictionary.lookup(query = query, direction = direction, sortMode = sortMode)
                }
                results = lookup
                isSearching = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                results = emptyList()
                lookupError = error.message ?: error::class.java.simpleName
                isSearching = false
            }
        }
    }

    if (debugRefreshKey > 0) {
        LaunchedEffect(debugRefreshKey) {
            debugStatus = withContext(Dispatchers.IO) {
                dictionary.debugStatus()
            }
        }
    }

    val selected = selectedResult
    if (selected != null) {
        EntryDetailScreen(
            result = selected,
            direction = direction,
            onBack = { selectedResult = null },
        )
        return
    }

    Scaffold(
        bottomBar = {
            DirectionNavBar(
                selectedDirection = direction,
                onDirectionSelected = {
                    selectedResult = null
                    directionName = it.name
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Header(
                debugStatus = debugStatus,
                onRefreshDebug = { debugRefreshKey += 1 },
            )
            Spacer(Modifier.height(18.dp))
            SearchBox(
                query = query,
                direction = direction,
                onQueryChange = {
                    selectedResult = null
                    when (direction) {
                        LookupDirection.PortugueseToEnglish -> portugueseQuery = it
                        LookupDirection.EnglishToPortuguese -> englishQuery = it
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            ResultSummary(
                query = query,
                direction = direction,
                results = results,
                error = lookupError,
                isSearching = isSearching,
            )
            Spacer(Modifier.height(10.dp))
            SortControls(
                selected = sortMode,
                onSelected = { sortModeName = it.name },
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(results) { result ->
                    ResultCard(
                        result = result,
                        direction = direction,
                        onClick = { selectedResult = result },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortControls(
    selected: SortMode,
    onSelected: (SortMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortButton(
            label = "Commonality",
            selected = selected == SortMode.Popularity,
            onClick = { onSelected(SortMode.Popularity) },
        )
        SortButton(
            label = "Alphabetical",
            selected = selected == SortMode.Alphabetical,
            onClick = { onSelected(SortMode.Alphabetical) },
        )
    }
}

@Composable
private fun SortButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Header(
    debugStatus: DictionaryDebugStatus?,
    onRefreshDebug: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PT",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EU-PTDicio+",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Português europeu -> English",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
        SettingsMenu(
            debugStatus = debugStatus,
            onRefreshDebug = onRefreshDebug,
        )
    }
}

@Composable
private fun SettingsMenu(
    debugStatus: DictionaryDebugStatus?,
    onRefreshDebug: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                expanded = true
                onRefreshDebug()
            },
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Debug checks") },
                onClick = onRefreshDebug,
            )
            DebugStatusRow("DB available", debugStatus?.schemaOk == true)
            DebugStatusRow("Asset bundled", debugStatus?.assetPresent == true)
            DebugStatusRow("Local copy", debugStatus?.localPresent == true)
            DebugStatusRow("Fallback search", debugStatus?.schemaOk == true)
            DebugStatusRow("FTS boost", debugStatus?.ftsOk == true)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Entries: ${debugStatus?.entryCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Signals: ${debugStatus?.frequencySignalCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Examples: ${debugStatus?.exampleCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Asset: ${debugStatus?.assetBytes?.toMb() ?: "?"} MB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Local: ${debugStatus?.localBytes?.toMb() ?: "?"} MB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = onRefreshDebug,
            )
            if (debugStatus != null && debugStatus.message != "OK") {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = debugStatus.message.take(80),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    onClick = onRefreshDebug,
                )
            }
        }
    }
}

@Composable
private fun DebugStatusRow(label: String, ok: Boolean) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            color = if (ok) Color(0xFF1D8F5A) else Color(0xFFD94C38),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(9.dp))
                Text(label)
            }
        },
        onClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBox(
    query: String,
    direction: LookupDirection,
    onQueryChange: (String) -> Unit,
) {
    val label = when (direction) {
        LookupDirection.PortugueseToEnglish -> "Search a Portuguese word"
        LookupDirection.EnglishToPortuguese -> "Search an English word"
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultSummary(
    query: String,
    direction: LookupDirection,
    results: List<LookupResult>,
    error: String?,
    isSearching: Boolean,
) {
    val emptyPrompt = when (direction) {
        LookupDirection.PortugueseToEnglish -> "Try falar, falávamos, cão, fazê-lo, or pôr"
        LookupDirection.EnglishToPortuguese -> "Try dog, good, speak, make, or thank you"
    }
    val text = when {
        error != null -> "Dictionary issue: ${error.take(90)}"
        isSearching -> "Searching..."
        query.isBlank() -> emptyPrompt
        results.isEmpty() -> "No local result yet"
        results.size == 1 -> "1 result"
        else -> "${results.size} results"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error == null) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.secondary
        },
    )
}

private fun Long.toMb(): Long = this / (1024L * 1024L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultCard(
    result: LookupResult,
    direction: LookupDirection,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.entry.lemma,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = result.entry.partOfSpeech.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                MatchBadge(matchType = result.matchType)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = result.entry.meanings.joinToString("; "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MatchNote(result = result, direction = direction)
            ResultSignals(result = result)
        }
    }
}

@Composable
private fun ResultSignals(result: LookupResult) {
    val formCount = result.entry.forms.size
    val exampleCount = result.entry.examples.size
    if (formCount == 0 && exampleCount == 0) return

    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (formCount > 0) {
            AssistChip(onClick = {}, label = { Text("$formCount forms") })
        }
        if (exampleCount > 0) {
            AssistChip(onClick = {}, label = { Text("$exampleCount examples") })
        }
    }
}

@Composable
private fun MatchNote(result: LookupResult, direction: LookupDirection) {
    val showNote = result.matchType == MatchType.InflectedForm ||
        result.matchType == MatchType.AccentInsensitive ||
        result.matchType == MatchType.EnglishMeaning
    if (!showNote) return

    val text = when (direction) {
        LookupDirection.PortugueseToEnglish -> "${result.matchedForm} -> ${result.entry.lemma}"
        LookupDirection.EnglishToPortuguese -> "${result.matchedForm} -> ${result.entry.lemma}"
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
    )
}

@Composable
private fun EntryDetailScreen(
    result: LookupResult,
    direction: LookupDirection,
    onBack: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            DetailTopBar(result = result, onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp),
            ) {
                item {
                    MeaningsSection(result = result)
                }
                if (result.entry.examples.isNotEmpty()) {
                    item {
                        ExamplesSection(result = result)
                    }
                }
                if (result.entry.forms.isNotEmpty()) {
                    item {
                        FormsSection(result = result)
                    }
                }
                if (result.entry.labels.isNotEmpty()) {
                    item {
                        SourceSection(result = result, direction = direction)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    result: LookupResult,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.entry.lemma,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.entry.partOfSpeech.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MeaningsSection(result: LookupResult) {
    DetailSection(title = "Meanings") {
        result.entry.meanings.forEachIndexed { index, meaning ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(30.dp),
                )
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExamplesSection(result: LookupResult) {
    DetailSection(title = "Examples") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            result.entry.examples.forEach { example ->
                val translation = example.translation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = example.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!translation.isNullOrBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormsSection(result: LookupResult) {
    val groups = result.formGroups()
    DetailSection(title = if (result.entry.partOfSpeech == PartOfSpeech.Verb) "Verb forms" else "Forms") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            groups.forEach { group ->
                Column {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.forms.forEach { form ->
                            FormChip(form = form)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormChip(form: DictionaryForm) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = form.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val label = form.learnerLabel()
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
        }
    }
}

@Composable
private fun SourceSection(
    result: LookupResult,
    direction: LookupDirection,
) {
    DetailSection(title = "Source") {
        MatchNote(result = result, direction = direction)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            result.entry.labels.forEach { label ->
                AssistChip(onClick = {}, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

private data class FormGroup(
    val title: String,
    val forms: List<DictionaryForm>,
)

private fun LookupResult.formGroups(): List<FormGroup> {
    val uniqueForms = entry.forms.distinctBy { it.text }
    if (entry.partOfSpeech != PartOfSpeech.Verb) {
        return listOf(FormGroup("Forms", uniqueForms.take(36)))
    }

    val specs = listOf(
        "Present" to { tags: Set<String> -> "present" in tags && "subjunctive" !in tags && "imperative" !in tags },
        "Past" to { tags: Set<String> -> tags.any { it in setOf("preterite", "imperfect", "pluperfect") } && "subjunctive" !in tags },
        "Future / conditional" to { tags: Set<String> -> tags.any { it in setOf("future", "conditional") } && "subjunctive" !in tags },
        "Subjunctive" to { tags: Set<String> -> "subjunctive" in tags },
        "Commands" to { tags: Set<String> -> "imperative" in tags },
        "Infinitive / gerund / participle" to { tags: Set<String> ->
            tags.any { it in setOf("infinitive", "gerund", "participle") }
        },
    )
    val used = hashSetOf<String>()
    val groups = specs.mapNotNull { (title, predicate) ->
        val forms = uniqueForms.filter { form ->
            val tags = form.tags.toSet()
            form.text !in used && predicate(tags)
        }.take(18)
        used.addAll(forms.map { it.text })
        if (forms.isEmpty()) null else FormGroup(title, forms)
    }.toMutableList()

    val other = uniqueForms.filter { it.text !in used }.take(18)
    if (other.isNotEmpty()) groups.add(FormGroup("Other forms", other))
    return groups
}

private fun DictionaryForm.learnerLabel(): String {
    val tags = tags.toSet()
    val person = when {
        "first-person" in tags && "singular" in tags -> "I"
        "second-person" in tags && "singular" in tags -> "you"
        "third-person" in tags && "singular" in tags -> "he/she"
        "first-person" in tags && "plural" in tags -> "we"
        "second-person" in tags && "plural" in tags -> "you pl."
        "third-person" in tags && "plural" in tags -> "they"
        else -> null
    }
    val tense = when {
        "present" in tags -> "present"
        "preterite" in tags -> "preterite"
        "imperfect" in tags -> "imperfect"
        "pluperfect" in tags -> "pluperfect"
        "future" in tags -> "future"
        "conditional" in tags -> "conditional"
        "subjunctive" in tags -> "subjunctive"
        "imperative" in tags && "negative" in tags -> "negative command"
        "imperative" in tags -> "command"
        "gerund" in tags -> "gerund"
        "infinitive" in tags -> "infinitive"
        "participle" in tags -> "participle"
        else -> null
    }
    return listOfNotNull(person, tense).distinct().joinToString(" · ")
}

@Composable
private fun MatchBadge(matchType: MatchType) {
    val label = when (matchType) {
        MatchType.ExactSurface -> "exact"
        MatchType.ExactLemma -> "lemma"
        MatchType.InflectedForm -> "form"
        MatchType.AccentInsensitive -> "accent"
        MatchType.Prefix -> "prefix"
        MatchType.EnglishMeaning -> "meaning"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun DirectionNavBar(
    selectedDirection: LookupDirection,
    onDirectionSelected: (LookupDirection) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth()) {
            DirectionNavItem(
                selected = selectedDirection == LookupDirection.PortugueseToEnglish,
                iconText = "PT",
                label = "PT -> Eng",
                modifier = Modifier.weight(1f),
                onClick = { onDirectionSelected(LookupDirection.PortugueseToEnglish) },
            )
            DirectionNavItem(
                selected = selectedDirection == LookupDirection.EnglishToPortuguese,
                iconText = "EN",
                label = "Eng -> PT",
                modifier = Modifier.weight(1f),
                onClick = { onDirectionSelected(LookupDirection.EnglishToPortuguese) },
            )
        }
    }
}

@Composable
private fun DirectionNavItem(
    selected: Boolean,
    iconText: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
        ) {
            Text(
                text = iconText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            },
        )
    }
}
