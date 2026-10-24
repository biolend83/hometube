package app.tubehaus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tubehaus.data.meta
import app.tubehaus.engine.ChannelPage
import app.tubehaus.engine.Page
import app.tubehaus.engine.PlaylistPage
import app.tubehaus.engine.Video
import app.tubehaus.engine.YouTube
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(app: AppState, id: String, name: String) {
    var tab by remember { mutableStateOf(YouTube.Tab.VIDEOS) }
    var head by remember { mutableStateOf<ChannelPage?>(null) }
    val scope = rememberCoroutineScope()
    val calm = app.store.settings.value.calm
    val tabs = listOfNotNull(
        YouTube.Tab.VIDEOS to "Videos",
        if (app.noShorts) null else YouTube.Tab.SHORTS to "Shorts",
        YouTube.Tab.LIVE to "Live",
        YouTube.Tab.PLAYLISTS to "Playlists",
    )
    val pager = rememberPager(id to tab, {
        val c = YouTube.channel(id, tab)
        if (head == null || tab == YouTube.Tab.VIDEOS) head = c
        c.items
    }, { YouTube.browseMore(it) })
    when {
        pager.items.isEmpty() && pager.error != null && head == null -> Problem(pager.error!!) { scope.launch { pager.load() } }
        else -> ItemGrid(
            pager.items, app, loading = pager.loading, onEnd = { pager.loadMore() },
            rows = tab == YouTube.Tab.PLAYLISTS,
            header = {
                Column {
                    val h = head
                    if (h != null && h.banner.isNotEmpty()) Img(h.banner, Modifier.fillMaxWidth().aspectRatio(6.2f))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(h?.avatar ?: "", 72)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(h?.name ?: name, style = MaterialTheme.typography.titleLarge)
                            val line = listOf(h?.handle ?: "", if (calm) "" else h?.subsText ?: "", h?.videoCountText ?: "").filter { it.isNotEmpty() }
                            Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp)) { SubscribeButton(id, h?.name ?: name, h?.avatar ?: "", app) }
                    PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0), edgePadding = 8.dp) {
                        for ((t, label) in tabs) Tab(tab == t, onClick = { tab = t }, text = { Text(tr(label)) })
                    }
                    if (pager.items.isEmpty() && pager.started && !pager.loading) Empty(tr("Nothing here"))
                }
            },
        )
    }
}

@Composable
fun PlaylistScreen(app: AppState, id: String, title: String) {
    var head by remember { mutableStateOf<PlaylistPage?>(null) }
    val scope = rememberCoroutineScope()
    val pager = rememberPager(id, {
        val p = YouTube.playlist(id)
        head = p
        Page(p.items.items, p.items.next)
    }, { YouTube.browseMore(it) })
    val videos = pager.items.filterIsInstance<Video>()
    if (pager.items.isEmpty() && pager.error != null) {
        Problem(pager.error!!) { scope.launch { pager.load() } }
        return
    }
    ItemGrid(
        pager.items, app, loading = pager.loading, onEnd = { pager.loadMore() }, rows = true,
        header = {
            Column(Modifier.padding(16.dp)) {
                Text(head?.title ?: title, style = MaterialTheme.typography.titleLarge)
                Text(listOf(head?.author ?: "", head?.countText ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { videos.firstOrNull()?.let { app.watch(it, videos.drop(1)) } }, enabled = videos.isNotEmpty()) { Text(tr("Play all")) }
                    OutlinedButton(onClick = {
                        // keep a copy on this device (the playlist on YouTube may change or vanish)
                        val pid = app.store.newPlaylist(head?.title ?: title, null)
                        app.store.lib.update { l ->
                            val pl = l.playlists[pid] ?: return@update l
                            l.copy(playlists = l.playlists + (pid to pl.copy(items = videos.map { it.meta() })))
                        }
                        app.toast(tr("Saved to your playlists"))
                    }, enabled = videos.isNotEmpty()) { Text(tr("Save to library")) }
                }
            }
        },
    )
}
