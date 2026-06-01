package com.euptdicio.wear

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import com.euptdicio.core.LookupDirection
import com.euptdicio.core.LookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearDictionaryApp(
                onClose = { finish() },
            )
        }
    }
}

@Composable
private fun WearDictionaryApp(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WearDictionaryRepository(context.applicationContext) }
    var direction by rememberSaveable { mutableStateOf<LookupDirection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberScalingLazyListState()
    val inputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val input = RemoteInput.getResultsFromIntent(result.data)
            ?.getCharSequence(INPUT_KEY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (input.isNotBlank()) {
            query = input
            submittedQuery = input
        }
    }

    LaunchedEffect(submittedQuery, direction) {
        val selectedDirection = direction
        if (submittedQuery.isBlank() || selectedDirection == null) {
            results = emptyList()
            error = null
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                repository.lookup(
                    query = submittedQuery,
                    direction = selectedDirection,
                    limit = 8,
                )
            }
        }.onSuccess { lookupResults ->
            results = lookupResults
        }.onFailure { throwable ->
            results = emptyList()
            error = throwable.message ?: throwable::class.java.simpleName
        }
        isLoading = false
    }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Header(direction = direction)
                }

                if (direction == null) {
                    item {
                        DirectionChip(
                            label = "English -> Portuguese",
                            onClick = {
                                direction = LookupDirection.EnglishToPortuguese
                                query = ""
                                submittedQuery = ""
                            },
                        )
                    }
                    item {
                        DirectionChip(
                            label = "Portuguese -> English",
                            onClick = {
                                direction = LookupDirection.PortugueseToEnglish
                                query = ""
                                submittedQuery = ""
                            },
                        )
                    }
                } else {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(0.86f),
                            label = {
                                Text(
                                    text = query.ifBlank { "Type word" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            secondaryLabel = {
                                Text(if (query.isBlank()) "Open keyboard" else "Tap to edit")
                            },
                            onClick = {
                                inputLauncher.launch(createTextInputIntent(direction?.displayLabel.orEmpty()))
                            },
                            colors = ChipDefaults.primaryChipColors(),
                        )
                    }
                    when {
                        isLoading -> item {
                            StatusText("Loading dictionary...")
                        }
                        error != null -> item {
                            StatusText("Lookup failed: $error")
                        }
                        submittedQuery.isNotBlank() && results.isEmpty() -> item {
                            StatusText("No results")
                        }
                    }
                    items(results) { result ->
                        ResultChip(result = result)
                    }
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            label = { Text("Change") },
                            onClick = {
                                direction = null
                                query = ""
                                submittedQuery = ""
                                results = emptyList()
                                error = null
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                        )
                    }
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            label = { Text("Close") },
                            onClick = onClose,
                            colors = ChipDefaults.secondaryChipColors(),
                        )
                    }
                }
            }
        }
    }
}

private fun createTextInputIntent(title: String) =
    RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        RemoteInputIntentHelper.putTitleExtra(intent, title)
        RemoteInputIntentHelper.putConfirmLabelExtra(intent, "Search")
        RemoteInputIntentHelper.putCancelLabelExtra(intent, "Cancel")
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent,
            listOf(
                RemoteInput.Builder(INPUT_KEY)
                    .setLabel("Word")
                    .build(),
            ),
        )
    }

@Composable
private fun Header(direction: LookupDirection?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "EU-PTDicio+",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = direction?.displayLabel ?: "Choose direction",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DirectionChip(
    label: String,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(0.86f),
        label = { Text(label) },
        onClick = onClick,
        colors = ChipDefaults.primaryChipColors(),
    )
}

@Composable
private fun StatusText(message: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .padding(vertical = 8.dp),
        text = message,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption1,
    )
}

@Composable
private fun ResultChip(result: LookupResult) {
    val meaning = result.entry.meanings.joinToString(", ")
    Chip(
        modifier = Modifier.fillMaxWidth(0.86f),
        label = {
            Text(
                text = result.entry.lemma,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = meaning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = {},
        colors = ChipDefaults.primaryChipColors(),
    )
}

private val LookupDirection.displayLabel: String
    get() = when (this) {
        LookupDirection.EnglishToPortuguese -> "English -> Portuguese"
        LookupDirection.PortugueseToEnglish -> "Portuguese -> English"
    }

private const val INPUT_KEY = "lookup_text"
