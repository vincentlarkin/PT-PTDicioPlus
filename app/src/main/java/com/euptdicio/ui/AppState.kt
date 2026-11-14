package com.euptdicio.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.euptdicio.core.Dictionary
import com.euptdicio.core.EntryDetail
import com.euptdicio.core.EntrySummary
import com.euptdicio.core.SearchResults
import com.euptdicio.data.DictionaryDatabase
import com.euptdicio.data.SavedWord
import com.euptdicio.data.Settings
import com.euptdicio.data.ThemeMode
import com.euptdicio.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { Search, Saved, About }

enum class AudioState { Idle, Loading, Playing, Failed }

/** All UI state. Lives as long as the activity (config changes are handled in place). */
class AppState(private val context: Context, private val scope: CoroutineScope) {
    private val store = UserStore(context)
    val settings = Settings(context)

    var dictionary by mutableStateOf<Dictionary?>(null); private set
    var installProgress by mutableStateOf<Float?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set

    var query by mutableStateOf(""); private set
    var results by mutableStateOf<SearchResults?>(null); private set
    var tab by mutableStateOf(Tab.Search)

    /** Entry pages, last = visible. Tapping a synonym pushes; back pops. */
    val stack = mutableStateListOf<EntryDetail>()
    val current: EntryDetail? get() = stack.lastOrNull()

    var favorites by mutableStateOf<List<SavedWord>>(emptyList()); private set
    var history by mutableStateOf<List<SavedWord>>(emptyList()); private set
    var wordOfDay by mutableStateOf<EntrySummary?>(null); private set
    var themeMode by mutableStateOf(settings.theme)
    var showBrazilianForms by mutableStateOf(settings.showBrazilianForms)
    var showBrazilianIpa by mutableStateOf(settings.showBrazilianIpa)
    var audio by mutableStateOf(AudioState.Idle); private set

    private var searchJob: Job? = null
    private var pendingQuery: String? = null
    private var pendingExternal: String? = null
    private var player: MediaPlayer? = null

    fun start() {
        scope.launch {
            try {
                val dict = withContext(Dispatchers.IO) {
                    if (!DictionaryDatabase.isInstalled(context)) installProgress = 0f
                    DictionaryDatabase.open(context) { p -> installProgress = p }
                }
                installProgress = null
                dictionary = dict
                refreshSaved()
                val day = System.currentTimeMillis() / 86_400_000L
                wordOfDay = withContext(Dispatchers.IO) { dict.randomCommonEntry(day) }
                pendingQuery?.let { pendingQuery = null; onQuery(it) }
                pendingExternal?.let { pendingExternal = null; externalLookup(it) }
            } catch (t: Throwable) {
                loadError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun onQuery(text: String) {
        query = text
        tab = Tab.Search
        val dict = dictionary ?: run { pendingQuery = text; return }
        searchJob?.cancel()
        if (text.isBlank()) {
            results = null
            return
        }
        searchJob = scope.launch {
            delay(35) // coalesce fast typing
            results = withContext(Dispatchers.IO) { dict.search(text) }
        }
    }

    /** Text arriving from the selection menu or a share. */
    fun externalLookup(text: String) {
        val clean = text.trim().trim('.', ',', ';', ':', '!', '?', '"', '“', '”', '«', '»', '(', ')').take(80)
        if (clean.isEmpty()) return
        if (dictionary == null) {
            query = clean
            pendingExternal = clean
            return
        }
        stack.clear()
        onQuery(clean)
        scope.launch {
            val dict = dictionary ?: return@launch
            val best = withContext(Dispatchers.IO) { dict.search(clean) }
            val hit = (if (best.englishFirst) best.english else best.portuguese).firstOrNull()
                ?: best.portuguese.firstOrNull() ?: best.english.firstOrNull()
            if (hit != null && hit.match.kind.code < 20) open(hit.entry.id)
        }
    }

    fun open(id: Long, replace: Boolean = false) {
        val dict = dictionary ?: return
        scope.launch {
            val detail = withContext(Dispatchers.IO) { dict.entry(id) } ?: return@launch
            stopAudio()
            if (replace && stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            if (stack.lastOrNull()?.summary?.id != id) stack.add(detail)
            val s = detail.summary
            withContext(Dispatchers.IO) { store.addHistory(SavedWord(s.word, s.pos, s.firstGloss, System.currentTimeMillis())) }
            refreshSaved()
        }
    }

    fun openWord(word: String) {
        val dict = dictionary ?: return
        scope.launch {
            val id = withContext(Dispatchers.IO) { dict.resolve(word) }
            if (id != null) open(id) else onQuery(word)
        }
    }

    fun openSaved(w: SavedWord) {
        val dict = dictionary ?: return
        scope.launch {
            val e = withContext(Dispatchers.IO) { dict.find(w.word, w.pos) }
            if (e != null) open(e.id) else onQuery(w.word)
        }
    }

    fun back(): Boolean {
        if (stack.isNotEmpty()) {
            stopAudio()
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (tab != Tab.Search) {
            tab = Tab.Search
            return true
        }
        if (query.isNotEmpty()) {
            onQuery("")
            return true
        }
        return false
    }

    fun closeEntries() {
        stopAudio()
        stack.clear()
    }

    fun isFavorite(e: EntryDetail) = favorites.any { it.word == e.summary.word && it.pos == e.summary.pos }

    fun toggleFavorite(e: EntryDetail) {
        val s = e.summary
        val on = !isFavorite(e)
        scope.launch {
            withContext(Dispatchers.IO) { store.setFavorite(SavedWord(s.word, s.pos, s.firstGloss, System.currentTimeMillis()), on) }
            refreshSaved()
        }
    }

    fun clearHistory() {
        scope.launch {
            withContext(Dispatchers.IO) { store.clearHistory() }
            refreshSaved()
        }
    }

    private suspend fun refreshSaved() {
        val (f, h) = withContext(Dispatchers.IO) { store.favorites() to store.history() }
        favorites = f
        history = h
    }

    fun setTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode }
    fun setBrazilianForms(on: Boolean) { showBrazilianForms = on; settings.showBrazilianForms = on }
    fun setBrazilianIpa(on: Boolean) { showBrazilianIpa = on; settings.showBrazilianIpa = on }

    fun playAudio(url: String) {
        stopAudio()
        audio = AudioState.Loading
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setOnPreparedListener { audio = AudioState.Playing; it.start() }
            setOnCompletionListener { audio = AudioState.Idle; it.release(); player = null }
            setOnErrorListener { mp, _, _ -> audio = AudioState.Failed; mp.release(); player = null; true }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (t: Throwable) {
                audio = AudioState.Failed
            }
        }
    }

    fun stopAudio() {
        player?.runCatching { stop(); release() }
        player = null
        audio = AudioState.Idle
    }
}
