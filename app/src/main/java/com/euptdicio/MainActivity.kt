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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euptdicio.core.InMemoryDictionary
import com.euptdicio.core.LookupDirection
import com.euptdicio.core.LookupResult
import com.euptdicio.core.MatchType
import com.euptdicio.core.SampleEntries

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
    val dictionary = remember { InMemoryDictionary(SampleEntries.europeanPortuguese) }
    var directionName by rememberSaveable { mutableStateOf(LookupDirection.PortugueseToEnglish.name) }
    var portugueseQuery by rememberSaveable { mutableStateOf("") }
    var englishQuery by rememberSaveable { mutableStateOf("") }
    val direction = LookupDirection.valueOf(directionName)
    val query = when (direction) {
        LookupDirection.PortugueseToEnglish -> portugueseQuery
        LookupDirection.EnglishToPortuguese -> englishQuery
    }
    val results = remember(query, direction) {
        dictionary.lookup(query = query, direction = direction)
    }

    Scaffold(
        bottomBar = {
            DirectionNavBar(
                selectedDirection = direction,
                onDirectionSelected = { directionName = it.name },
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
            Header()
            Spacer(Modifier.height(18.dp))
            SearchBox(
                query = query,
                direction = direction,
                onQueryChange = {
                    when (direction) {
                        LookupDirection.PortugueseToEnglish -> portugueseQuery = it
                        LookupDirection.EnglishToPortuguese -> englishQuery = it
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            ResultSummary(query = query, direction = direction, results = results)
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(results) { result ->
                    ResultCard(
                        result = result,
                        direction = direction,
                        onClick = {
                            when (direction) {
                                LookupDirection.PortugueseToEnglish -> portugueseQuery = result.entry.lemma
                                LookupDirection.EnglishToPortuguese -> englishQuery = result.matchedForm
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        Column {
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
    }
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
) {
    val emptyPrompt = when (direction) {
        LookupDirection.PortugueseToEnglish -> "Try falar, falávamos, cão, faze-lo, or pôr"
        LookupDirection.EnglishToPortuguese -> "Try dog, good, speak, make, or thank you"
    }
    val text = when {
        query.isBlank() -> emptyPrompt
        results.isEmpty() -> "No local result yet"
        results.size == 1 -> "1 result"
        else -> "${results.size} results"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
    )
}

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
            )
            MatchNote(result = result, direction = direction)
            FormsSection(forms = result.entry.forms)
            if (result.entry.labels.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.entry.labels.forEach { label ->
                        AssistChip(onClick = {}, label = { Text(label) })
                    }
                }
            }
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
private fun FormsSection(forms: List<String>) {
    if (forms.isEmpty()) return

    val visibleForms = forms.take(10)
    val hiddenCount = forms.size - visibleForms.size

    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = "Forms",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            visibleForms.forEach { form ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = form,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (hiddenCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "+$hiddenCount",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
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
