package app.tubehaus.ui

import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tubehaus.engine.Video
import app.tubehaus.engine.YouTube
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(app: AppState, initial: String) {
    var text by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf(initial) }
    var filter by remember { mutableStateOf(YouTube.Filter.ALL) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun submit(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        suggestions = emptyList()
        // a pasted link opens what it points to
        if (t.startsWith("http") || t.startsWith("@") || t.contains("youtu")) {
            scope.launch {
                val r = runCatching { YouTube.resolve(t) }.getOrNull()
                when (r?.first) {
                    "video" -> app.watch(Video(r.second, ""))
                    "playlist" -> app.go(Screen.Playlist(r.second))
                    "channel" -> app.go(Screen.Channel(r.second))
                    else -> { query = t }
                }
            }
            return
        }
        query = t
        app.stack[app.stack.size - 1] = Screen.Search(t)
    }

    LaunchedEffect(text) {
        if (text == query || text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(200)
        suggestions = runCatching { YouTube.suggestions(text) }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) { if (initial.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
            OutlinedTextField(
                text, { text = it },
                Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text(tr("Search YouTube")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = ""; runCatching { focus.requestFocus() } }) { Icon(Icons.Outlined.Close, tr("Clear")) }
                },
            )
            IconButton(onClick = { submit(text) }) { Icon(Icons.Outlined.Search, tr("Search")) }
        }
        if (suggestions.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(suggestions) { s ->
                    Row(Modifier.fillMaxWidth().clickable { text = s; submit(s) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null)
                        Text(s, Modifier.padding(start = 16.dp))
                    }
                }
            }
            return@Column
        }
        if (query.isEmpty()) return@Column
        val compact = LocalCompact.current
        val grid = app.store.settings.value.searchGrid
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(YouTube.Filter.ALL to "All", YouTube.Filter.VIDEOS to "Videos", YouTube.Filter.CHANNELS to "Channels", YouTube.Filter.PLAYLISTS to "Playlists")) { (f, label) ->
                    FilterChip(filter == f, onClick = { filter = f }, label = { Text(tr(label)) })
                }
            }
            // phones always show one card per row; wider screens can pick a list or a grid
            if (!compact) IconButton(onClick = { app.store.settings.update { it.copy(searchGrid = !grid) } }) {
                if (grid) Icon(Icons.AutoMirrored.Outlined.ViewList, tr("Show as a list"))
                else Icon(Icons.Outlined.GridView, tr("Show as a grid"))
            }
        }
        val pager = rememberPager(query to filter, { YouTube.search(query, filter) }, { YouTube.searchMore(it) })
        when {
            pager.items.isEmpty() && pager.loading -> Busy()
            pager.items.isEmpty() && pager.error != null -> Problem(pager.error!!) { scope.launch { pager.load() } }
            pager.items.isEmpty() && pager.started -> Empty(tr("No results"))
            else -> ItemGrid(
                pager.items.filter { !(app.noShorts && it is Video && it.short) }, app,
                loading = pager.loading, onEnd = { pager.loadMore() }, rows = !compact && !grid,
            )
        }
    }
}
