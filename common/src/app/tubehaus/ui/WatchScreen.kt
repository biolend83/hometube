package app.tubehaus.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tubehaus.engine.Channel
import app.tubehaus.engine.Comment
import app.tubehaus.engine.Playlist
import app.tubehaus.engine.Video
import app.tubehaus.engine.YouTube
import app.tubehaus.rec.Learn
import kotlinx.coroutines.launch

@Composable
fun WatchScreen(app: AppState) {
    val pb = app.playback
    val v = pb.video
    if (v == null) {
        Empty(tr("Nothing is playing"))
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 1000.dp
        if (wide) {
            // desktop: the video and its details on the left, what's next on the right
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                    item { PlayerBox(app) }
                    item { Info(app, v) }
                    comments(app)
                }
                LazyColumn(Modifier.width(420.dp).padding(top = 16.dp, end = 8.dp)) { related(app) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerBox(app)
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Info(app, v) }
                    item { CommentsTeaser(app) }
                    related(app)
                }
            }
        }
    }
}

@Composable
private fun PlayerBox(app: AppState) {
    val pb = app.playback
    val st by app.platform.player.state.collectAsState()
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        val v = pb.video
        when {
            pb.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pb.error!!, color = Color.White)
                Button(onClick = { pb.retry() }, modifier = Modifier.padding(top = 12.dp)) { Text(tr("Try again")) }
            }
            pb.loading || st.videoId != v?.id -> {
                if (v != null) Img(v.thumbHigh, Modifier.fillMaxSize())
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            else -> VideoArea(app, Modifier.fillMaxSize())
        }
        // while loading there are no controls yet: keep a way back
        if ((pb.error != null || pb.loading || st.videoId != v?.id)) {
            IconButton(onClick = { app.back() }, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Color.White)
            }
        }
    }
    st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
}

@Composable
private fun Info(app: AppState, v: Video) {
    val pb = app.playback
    val d = pb.details
    val s = pb.stream
    val lib by app.store.lib.state.collectAsState()
    val calm = app.store.settings.value.calm
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = if (expanded) 10 else 2, overflow = TextOverflow.Ellipsis)
        val line = listOfNotNull(
            (d?.viewsText?.takeIf { it.isNotEmpty() } ?: v.views.takeIf { it > 0 }?.let { tr("{n} views").replace("{n}", compact(it)) }).takeIf { !calm },
            d?.publishedText?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        // channel
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { if (v.channelId.isNotEmpty()) app.go(Screen.Channel(v.channelId, v.author)) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(d?.avatar ?: lib.subs[v.channelId]?.thumb ?: "", 40)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(v.author, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!calm && !d?.subsText.isNullOrEmpty()) Text(d!!.subsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (v.channelId.isNotEmpty()) SubscribeButton(v.channelId, v.author, d?.avatar ?: "", app)
        }
        Actions(app, v)
        // description
        val desc = s?.description ?: ""
        if (desc.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }.padding(12.dp).animateContentSize(),
            ) {
                if (expanded) SelectionContainer { Text(desc, style = MaterialTheme.typography.bodyMedium) }
                else Text(desc, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(tr(if (expanded) "Show less" else "...more"), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Actions(app: AppState, v: Video) {
    val pb = app.playback
    val lib by app.store.lib.state.collectAsState()
    val liked = lib.likes.any { it.id == v.id }
    val disliked = v.id in lib.dislikes
    val saved = lib.wl.any { it.id == v.id }
    var pick by remember { mutableStateOf(false) }
    val calm = app.store.settings.value.calm
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Chip(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, (pb.details?.likesText?.takeIf { !calm } ?: "").ifEmpty { tr("Like") }) {
                app.store.setInList("likes", v, !liked)
                if (!liked) Learn.nudgeChannel(app.store, v.channelId, v.author, 1.0)
            }
        }
        item {
            Chip(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, tr("Dislike")) {
                app.store.setInList("dislikes", v, !disliked)
                if (!disliked) Learn.nudgeChannel(app.store, v.channelId, v.author, -1.5)
            }
        }
        item { Chip(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, tr("Watch later")) { app.store.setInList("wl", v, !saved) } }
        item { Chip(Icons.Outlined.LibraryAdd, tr("Save")) { pick = true } }
        item { Chip(Icons.Outlined.Share, tr("Share")) { app.share(v.title, "https://youtu.be/${v.id}") } }
        if (pb.next != null) item { Chip(Icons.Outlined.SkipNext, tr("Next")) { pb.skipNext() } }
    }
    if (pick) PlaylistPicker(v, app) { pick = false }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick, label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(18.dp),
        border = null,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun LazyListScope.related(app: AppState) {
    val pb = app.playback
    val queue = pb.queue
    if (queue.isNotEmpty()) {
        item { SectionTitle(tr("Up next")) }
        items(queue, key = { "q" + it.id }) { v ->
            VideoRow(v, app) { pb.open(v, queue.dropWhile { it.id != v.id }.drop(1)); app.stack[app.stack.size - 1] = Screen.Watch(v.id) }
        }
    }
    val d = pb.details ?: run { item { Busy() }; return }
    val calm = app.store.settings.value.calm
    items(d.related.filter { !(calm && it is Video && it.short) }, key = { "r" + it.id }) {
        when (it) {
            is Video -> VideoRow(it, app)
            is Playlist -> PlaylistRow(it, app)
            is Channel -> ChannelRow(it, app)
        }
    }
}

@Composable
private fun CommentsTeaser(app: AppState) {
    var open by remember { mutableStateOf(false) }
    val token = app.playback.details?.commentsToken ?: return
    if (!open) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable { open = true }.padding(12.dp),
        ) { Text(tr("Comments"), style = MaterialTheme.typography.titleSmall) }
        return
    }
    Column { CommentList(app, token) }
}

private fun LazyListScope.comments(app: AppState) {
    item {
        val token = app.playback.details?.commentsToken
        if (token != null) {
            SectionTitle(tr("Comments"))
            CommentList(app, token)
        }
    }
}

@Composable
private fun CommentList(app: AppState, token: String) {
    val pager = rememberPager(token, { YouTube.comments(token) }, { YouTube.comments(it) })
    val scope = rememberCoroutineScope()
    Column {
        if (pager.items.isEmpty() && pager.loading) Busy()
        if (pager.items.isEmpty() && pager.started && !pager.loading) Empty(tr("No comments"))
        for (c in pager.items) CommentView(app, c)
        if (pager.next != null) TextButton(onClick = { scope.launch { pager.loadMore() } }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(tr(if (pager.loading) "Loading..." else "More comments"))
        }
    }
}

@Composable
private fun CommentView(app: AppState, c: Comment, reply: Boolean = false) {
    var showReplies by remember { mutableStateOf(false) }
    val calm = app.store.settings.value.calm
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 52.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Avatar(c.avatar, if (reply) 24 else 32, Modifier.clickable { if (c.channelId.isNotEmpty()) app.go(Screen.Channel(c.channelId, c.author)) })
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                listOf(c.author, c.published).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (c.byCreator) FontWeight.Bold else null,
            )
            SelectionContainer { Text(c.text, style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!calm && c.likes.isNotEmpty()) {
                    Icon(Icons.Outlined.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.likes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    Spacer(Modifier.width(16.dp))
                }
                if (c.replies > 0 && c.repliesToken != null) TextButton(onClick = { showReplies = !showReplies }, contentPadding = PaddingValues(0.dp)) {
                    Text(tr("{n} replies").replace("{n}", "${c.replies}"), color = Blue)
                }
            }
        }
    }
    if (showReplies && c.repliesToken != null) {
        val pager = rememberPager(c.repliesToken, { YouTube.comments(c.repliesToken) }, { YouTube.comments(it) })
        val scope = rememberCoroutineScope()
        for (r in pager.items) CommentView(app, r, reply = true)
        if (pager.loading) Busy()
        if (pager.next != null && !pager.loading) TextButton(onClick = { scope.launch { pager.loadMore() } }, modifier = Modifier.padding(start = 52.dp)) {
            Text(tr("More replies"))
        }
    }
}

