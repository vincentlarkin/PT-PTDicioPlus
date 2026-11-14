package com.euptdicio.wear

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.euptdicio.core.Dictionary
import com.euptdicio.core.EntryDetail
import com.euptdicio.core.SearchHit
import com.euptdicio.core.Variety
import com.euptdicio.data.DictionaryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INPUT_KEY = "query"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WearApp() } }
    }
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dict by remember { mutableStateOf<Dictionary?>(null) }
    var status by remember { mutableStateOf("A preparar…") }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var entry by remember { mutableStateOf<EntryDetail?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { DictionaryDatabase.open(context) } }
            .onSuccess { dict = it; status = "" }
            .onFailure { status = it.message ?: "Error" }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(INPUT_KEY)?.toString()?.trim()
        val d = dict
        if (!text.isNullOrEmpty() && d != null) {
            query = text
            entry = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { d.search(text, 12) }
                hits = if (r.englishFirst) r.english + r.portuguese else r.portuguese + r.english
                hits = hits.distinctBy { it.entry.id }
            }
        }
    }

    BackHandler(enabled = entry != null) { entry = null }
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            val e = entry
            if (e != null) {
                item { EntryHeader(e) }
                items(e.senses.take(6)) { s ->
                    Text(
                        (if (s.labels.isNotEmpty()) "(${s.labels.joinToString(", ")}) " else "") + s.gloss,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            } else {
                item {
                    Text("EU-PTDicio+", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Chip(
                        onClick = {
                            launcher.launch(RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
                                RemoteInputIntentHelper.putTitleExtra(intent, "Português / English")
                                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(INPUT_KEY).setLabel("Palavra").build()))
                            })
                        },
                        enabled = dict != null,
                        label = { Text(if (query.isEmpty()) "Pesquisar" else query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { if (status.isNotEmpty()) Text(status) else Text("voz ou teclado") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(hits, key = { it.entry.id }) { h ->
                    Chip(
                        onClick = {
                            val d = dict ?: return@Chip
                            scope.launch { entry = withContext(Dispatchers.IO) { d.entry(h.entry.id) } }
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        label = { Text(h.entry.word + regionMark(h.entry.variety), maxLines = 1) },
                        secondaryLabel = { Text(h.entry.firstGloss, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty() && hits.isEmpty()) item { Text("Sem resultados", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

private fun regionMark(variety: Int) = when {
    variety and Variety.PORTUGAL != 0 -> "  · PT"
    variety and Variety.BRAZIL != 0 -> "  · BR"
    else -> ""
}

@Composable
private fun EntryHeader(e: EntryDetail) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(e.summary.word, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(listOfNotNull(e.summary.ipa, e.summary.pos).joinToString("  "), color = Color(0xFFA8C3F5),
            style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
