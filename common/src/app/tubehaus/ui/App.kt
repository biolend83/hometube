package app.tubehaus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tubehaus.engine.InnerTube
import app.tubehaus.engine.Net
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

@Composable
fun App(app: AppState) {
    val settings by app.store.settings.state.collectAsState()
    val lang = settings.lang.ifEmpty { "en" }
    L.lang = lang
    InnerTube.hl = lang
    InnerTube.gl = settings.region.ifEmpty { app.platform.deviceRegion() }.uppercase()
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.client })) }
            .diskCache { DiskCache.Builder().directory(java.io.File(app.platform.cacheDir, "images").toOkioPath()).maxSizeBytes(150L shl 20).build() }
            .crossfade(true)
            .build()
    }
    key(lang) {
        TubeTheme(settings.dark) {
            CompositionLocalProvider(LocalPlatform provides app.platform) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        settings.lang.isEmpty() -> LanguagePicker(app)
                        !settings.welcomed && app.store.lib.value.subs.isEmpty() -> Welcome(app)
                        app.pip -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                            app.platform.player.View(Modifier.fillMaxSize())
                        }
                        app.fullscreen && app.current is Screen.Watch -> VideoArea(app, Modifier.fillMaxSize(), fullscreen = true)
                        else -> Shell(app)
                    }
                }
            }
        }
    }
}

private data class Tab(val screen: Screen, val label: String, val on: ImageVector, val off: ImageVector)

@Composable
private fun tabs(app: AppState): List<Tab> {
    val calm = app.store.settings.value.calm
    return listOfNotNull(
        Tab(Screen.Home, tr("Home"), Icons.Filled.Home, Icons.Outlined.Home),
        if (calm) null else Tab(Screen.Shorts, tr("Shorts"), Icons.Filled.Bolt, Icons.Outlined.Bolt),
        Tab(Screen.Subs, tr("Subscriptions"), Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
        Tab(Screen.Library, tr("Library"), Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    )
}

@Composable
private fun Shell(app: AppState) {
    val cur = app.current
    val touch = app.platform.touch
    val root = app.stack.firstOrNull()
    val showTop = cur !is Screen.Watch && cur !is Screen.Shorts && cur !is Screen.Search
    LaunchedEffect(cur) {
        // Shorts has its own player; a video playing in the background stops
        if (cur is Screen.Shorts) app.playback.close()
    }
    Scaffold(
        topBar = { if (showTop) TopBar(app) },
        bottomBar = {
            Column {
                if (app.playback.video != null && cur !is Screen.Watch && cur !is Screen.Shorts) MiniPlayer(app)
                if (touch && cur !is Screen.Watch) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        for (t in tabs(app)) {
                            val sel = root == t.screen
                            NavigationBarItem(
                                selected = sel, onClick = { app.go(t.screen) },
                                icon = { Icon(if (sel) t.on else t.off, null) },
                                label = { Text(t.label, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(app.snackbar) },
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            if (!touch) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    for (t in tabs(app)) {
                        val sel = root == t.screen
                        NavigationRailItem(
                            selected = sel, onClick = { app.go(t.screen) },
                            icon = { Icon(if (sel) t.on else t.off, null) },
                            label = { Text(t.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                key(cur) {
                    when (cur) {
                        Screen.Home -> HomeScreen(app)
                        Screen.Shorts -> ShortsScreen(app)
                        Screen.Subs -> SubsScreen(app)
                        Screen.Library -> LibraryScreen(app)
                        Screen.Settings -> SettingsScreen(app)
                        Screen.Learned -> LearnedScreen(app)
                        Screen.History -> HistoryScreen(app)
                        is Screen.Search -> SearchScreen(app, cur.query)
                        is Screen.Watch -> WatchScreen(app)
                        is Screen.Channel -> ChannelScreen(app, cur.id, cur.name)
                        is Screen.Playlist -> PlaylistScreen(app, cur.id, cur.title)
                        is Screen.Local -> LocalListScreen(app, cur.key)
                    }
                }
            }
        }
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 28.dp, height = 20.dp).clip(RoundedCornerShape(6.dp)).background(Red), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("TubeHaus", fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.5).sp)
    }
}

@Composable
private fun TopBar(app: AppState) {
    Column {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.stack.size > 1) {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
            }
            Logo(Modifier.clickable { app.go(Screen.Home) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { app.go(Screen.Search()) }) { Icon(Icons.Outlined.Search, tr("Search")) }
            IconButton(onClick = { app.go(Screen.Settings) }) { Icon(Icons.Outlined.Settings, tr("Settings")) }
        }
        if (!app.platform.touch) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** The bar above the tabs while a video plays and you look at something else. */
@Composable
private fun MiniPlayer(app: AppState) {
    val v = app.playback.video ?: return
    val st by app.platform.player.state.collectAsState()
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (st.durationMs > 0) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline)) {
                Box(Modifier.fillMaxWidth((st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f)).height(2.dp).background(Red))
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { app.go(Screen.Watch(v.id)) }.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Img(v.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { if (st.playing) app.platform.player.pause() else app.platform.player.play() }) {
                Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr(if (st.playing) "Pause" else "Play"))
            }
            IconButton(onClick = { app.playback.close() }) { Icon(Icons.Filled.Close, tr("Close")) }
        }
    }
}
