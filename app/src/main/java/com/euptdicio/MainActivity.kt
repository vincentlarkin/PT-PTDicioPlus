package com.euptdicio

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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

private const val REPOSITORY_URL = "https://github.com/vincentlarkin/PT-PTDicioPlus"
private const val UI_STATE_PREFS = "dictionary_ui_state"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ),
        )
        setContent {
            DictionaryAppRoot()
        }
    }
}

@Composable
private fun DictionaryAppRoot() {
    val context = LocalContext.current
    val uiStateStore = remember { DictionaryUiStateStore(context.applicationContext) }
    var darkTheme by rememberSaveable { mutableStateOf(uiStateStore.darkTheme) }

    EUPTDicioTheme(darkTheme = darkTheme) {
        DictionaryApp(
            uiStateStore = uiStateStore,
            darkTheme = darkTheme,
            onDarkThemeChange = { darkTheme = it },
        )
    }
}

@Composable
private fun EUPTDicioTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    AndroidColor.TRANSPARENT,
                    AndroidColor.TRANSPARENT,
                )
            },
        )
    }

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF43B39F),
            onPrimary = Color(0xFF09211D),
            secondary = Color(0xFFFF7B62),
            background = Color(0xFF111513),
            surface = Color(0xFF1A201D),
            surfaceVariant = Color(0xFF2D3733),
            onSurface = Color(0xFFE8F1EC),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF176B5D),
            onPrimary = Color.White,
            secondary = Color(0xFFD94C38),
            background = Color(0xFFFBF8F1),
            surface = Color(0xFFFFFCF6),
            surfaceVariant = Color(0xFFE9E2D6),
            onSurface = Color(0xFF1E2422),
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}

private class DictionaryUiStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(UI_STATE_PREFS, Context.MODE_PRIVATE)

    val darkTheme: Boolean
        get() = preferences.getBoolean(KEY_DARK_THEME, false)
    val directionName: String
        get() = preferences.getString(KEY_DIRECTION, LookupDirection.PortugueseToEnglish.name)
            ?: LookupDirection.PortugueseToEnglish.name
    val portugueseQuery: String
        get() = preferences.getString(KEY_PORTUGUESE_QUERY, "").orEmpty()
    val englishQuery: String
        get() = preferences.getString(KEY_ENGLISH_QUERY, "").orEmpty()
    val sortModeName: String
        get() = preferences.getString(KEY_SORT_MODE, SortMode.Popularity.name) ?: SortMode.Popularity.name
    val selectedResultId: Long?
        get() = if (preferences.contains(KEY_SELECTED_RESULT_ID)) {
            preferences.getLong(KEY_SELECTED_RESULT_ID, 0L)
        } else {
            null
        }

    fun save(
        darkTheme: Boolean,
        directionName: String,
        portugueseQuery: String,
        englishQuery: String,
        sortModeName: String,
        selectedResultId: Long?,
    ) {
        preferences.edit()
            .putBoolean(KEY_DARK_THEME, darkTheme)
            .putString(KEY_DIRECTION, directionName)
            .putString(KEY_PORTUGUESE_QUERY, portugueseQuery)
            .putString(KEY_ENGLISH_QUERY, englishQuery)
            .putString(KEY_SORT_MODE, sortModeName)
            .also { editor ->
                if (selectedResultId == null) {
                    editor.remove(KEY_SELECTED_RESULT_ID)
                } else {
                    editor.putLong(KEY_SELECTED_RESULT_ID, selectedResultId)
                }
            }
            .apply()
    }

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_DIRECTION = "direction"
        const val KEY_PORTUGUESE_QUERY = "portuguese_query"
        const val KEY_ENGLISH_QUERY = "english_query"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_SELECTED_RESULT_ID = "selected_result_id"
    }
}

@Composable
private fun DictionaryApp(
    uiStateStore: DictionaryUiStateStore,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val dictionary = remember { DictionaryRepository(context.applicationContext) }
    var directionName by rememberSaveable { mutableStateOf(uiStateStore.directionName) }
    var portugueseQuery by rememberSaveable { mutableStateOf(uiStateStore.portugueseQuery) }
    var englishQuery by rememberSaveable { mutableStateOf(uiStateStore.englishQuery) }
    var sortModeName by rememberSaveable { mutableStateOf(uiStateStore.sortModeName) }
    var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
    var selectedResultId by rememberSaveable { mutableStateOf(uiStateStore.selectedResultId) }
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

    LaunchedEffect(
        darkTheme,
        directionName,
        portugueseQuery,
        englishQuery,
        sortModeName,
        selectedResultId,
    ) {
        uiStateStore.save(
            darkTheme = darkTheme,
            directionName = directionName,
            portugueseQuery = portugueseQuery,
            englishQuery = englishQuery,
            sortModeName = sortModeName,
            selectedResultId = selectedResultId,
        )
    }

    LaunchedEffect(query, direction, sortMode) {
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

    val selected = selectedResultId?.let { selectedId ->
        results.firstOrNull { it.entryId == selectedId }
    }
    BackHandler(enabled = selectedResultId != null) {
        selectedResultId = null
    }
    BackHandler(enabled = selectedResultId == null) {
        activity?.moveTaskToBack(true)
    }

    if (selected != null) {
        EntryDetailScreen(
            result = selected,
            direction = direction,
            onBack = { selectedResultId = null },
        )
        return
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                debugStatus = debugStatus,
                onRefreshDebug = { debugRefreshKey += 1 },
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
            )
        },
        bottomBar = {
            DirectionNavBar(
                selectedDirection = direction,
                onDirectionSelected = {
                    selectedResultId = null
                    directionName = it.name
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            SearchBox(
                query = query,
                direction = direction,
                onQueryChange = {
                    selectedResultId = null
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
                        onClick = { selectedResultId = result.entryId },
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
private fun SearchTopBar(
    debugStatus: DictionaryDebugStatus?,
    onRefreshDebug: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(66.dp)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f), CircleShape),
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "Português europeu -> English",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
            }
            SettingsMenu(
                debugStatus = debugStatus,
                onRefreshDebug = onRefreshDebug,
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                iconTint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SettingsMenu(
    debugStatus: DictionaryDebugStatus?,
    onRefreshDebug: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val menuContainer = Color(0xFF17211E)
    val menuText = Color(0xFFF3F7F2)
    val menuSubtle = Color(0xFFC6D5CF)

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
                tint = iconTint,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = menuContainer,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (darkTheme) "Dark mode" else "Light mode",
                        color = menuText,
                    )
                },
                trailingIcon = {
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                    )
                },
                onClick = { onDarkThemeChange(!darkTheme) },
            )
            DropdownMenuItem(
                text = { Text("Debug checks", color = menuText) },
                onClick = onRefreshDebug,
            )
            DebugStatusRow("DB available", debugStatus?.schemaOk == true, menuText)
            DebugStatusRow("Asset bundled", debugStatus?.assetPresent == true, menuText)
            DebugStatusRow("Local copy", debugStatus?.localPresent == true, menuText)
            DebugStatusRow("Fallback search", debugStatus?.schemaOk == true, menuText)
            DebugStatusRow("FTS boost", debugStatus?.ftsOk == true, menuText)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Entries: ${debugStatus?.entryCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = menuSubtle,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Signals: ${debugStatus?.frequencySignalCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = menuSubtle,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Examples: ${debugStatus?.exampleCount ?: "checking"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = menuSubtle,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Asset: ${debugStatus?.assetBytes?.toMb() ?: "?"} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = menuSubtle,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Local: ${debugStatus?.localBytes?.toMb() ?: "?"} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = menuSubtle,
                    )
                },
                onClick = onRefreshDebug,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "GitHub repo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = menuText,
                    )
                },
                onClick = {
                    expanded = false
                    uriHandler.openUri(REPOSITORY_URL)
                },
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
private fun DebugStatusRow(label: String, ok: Boolean, textColor: Color) {
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
                Text(label, color = textColor)
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
    Scaffold(
        topBar = {
            DetailTopBar(onBack = onBack)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp),
            ) {
                item {
                    EntryHeader(result = result)
                }
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
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(66.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "EU-PTDicio+",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EntryHeader(result: LookupResult) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Text(
            text = result.entry.lemma,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {},
                label = { Text(result.entry.partOfSpeech.displayName) },
            )
            if (result.matchType != MatchType.ExactLemma) {
                AssistChip(
                    onClick = {},
                    label = { Text("Matched ${result.matchedForm}") },
                )
            }
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
    var showAllForms by rememberSaveable(result.entryId) { mutableStateOf(false) }
    val commonGroups = result.formGroups(commonOnly = true)
    val allGroups = result.formGroups(commonOnly = false)
    val groups = if (showAllForms) allGroups else commonGroups
    val hiddenCount = (result.entry.forms.distinctBy { it.text }.size - commonGroups.sumOf { it.forms.size })
        .coerceAtLeast(0)
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
            if (hiddenCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showAllForms = !showAllForms },
                ) {
                    Text(
                        text = if (showAllForms) {
                            "Show common forms"
                        } else {
                            "Show all forms ($hiddenCount more)"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
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

private fun LookupResult.formGroups(commonOnly: Boolean): List<FormGroup> {
    val uniqueForms = entry.forms.distinctBy { it.text }
    if (entry.partOfSpeech != PartOfSpeech.Verb) {
        return listOf(FormGroup("Forms", uniqueForms.take(if (commonOnly) 12 else 72)))
    }

    if (commonOnly) {
        return commonVerbFormGroups(uniqueForms)
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

private fun commonVerbFormGroups(forms: List<DictionaryForm>): List<FormGroup> {
    val used = hashSetOf<String>()
    fun takeGroup(
        title: String,
        limit: Int,
        predicate: (Set<String>) -> Boolean,
    ): FormGroup? {
        val selected = forms.filter { form ->
            val tags = form.tags.toSet()
            form.text !in used && predicate(tags)
        }.take(limit)
        used.addAll(selected.map { it.text })
        return if (selected.isEmpty()) null else FormGroup(title, selected)
    }

    return listOfNotNull(
        takeGroup("Present", 6) { tags ->
            "present" in tags && "subjunctive" !in tags && "imperative" !in tags
        },
        takeGroup("Past", 8) { tags ->
            tags.any { it in setOf("preterite", "imperfect") } && "subjunctive" !in tags
        },
        takeGroup("Everyday building blocks", 6) { tags ->
            tags.any { it in setOf("infinitive", "gerund", "participle") }
        },
        takeGroup("Useful advanced forms", 6) { tags ->
            "subjunctive" in tags || "imperative" in tags || "future" in tags || "conditional" in tags
        },
    )
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
    return listOfNotNull(person, tense).distinct().joinToString(" - ")
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
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
