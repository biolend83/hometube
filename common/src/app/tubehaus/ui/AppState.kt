package app.tubehaus.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tubehaus.data.Store
import app.tubehaus.engine.InnerTube
import app.tubehaus.engine.Video
import app.tubehaus.rec.Feed
import app.tubehaus.rec.Recommender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Shorts : Screen
    data object Subs : Screen
    data object Library : Screen
    data object Settings : Screen
    data object Learned : Screen
    data object History : Screen
    data class Search(val query: String = "") : Screen
    data class Watch(val id: String) : Screen
    data class Channel(val id: String, val name: String = "") : Screen
    data class Playlist(val id: String, val title: String = "") : Screen
    /** a list kept on this device: "wl", "likes" or one of your playlists (HP...) */
    data class Local(val key: String) : Screen
}

val TABS = listOf(Screen.Home, Screen.Shorts, Screen.Subs, Screen.Library)

class AppState(val platform: Platform, val store: Store) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    init {
        // the YouTube visitor identity is kept with the app's data (never in backups or exports)
        InnerTube.identityFile = java.io.File(platform.dataDir, "identity.json")
    }

    val feed = Feed(store)
    val rec = Recommender(store, feed)
    val playback = Playback(this)
    val snackbar = SnackbarHostState()

    var fullscreen by mutableStateOf(false)
    /** Android picture-in-picture: only the video is drawn */
    var pip by mutableStateOf(false)
    /** the Shorts screen's keys while it is open (up and down change the Short) */
    var shortsKeys: ((androidx.compose.ui.input.key.Key) -> Boolean)? = null

    /** Shorts are hidden everywhere: by their own switch or by calm mode */
    val noShorts: Boolean get() = store.settings.value.let { it.calm || it.hideShorts }

    fun go(s: Screen) {
        if (s in TABS) {
            stack.clear(); stack.add(s)
            return
        }
        // opening the video that is already open just returns to it
        if (s is Screen.Watch) stack.removeAll { it is Screen.Watch }
        stack.add(s)
    }

    /** false when there is nowhere to go back to (the app may close) */
    fun back(): Boolean {
        if (fullscreen) { setFull(false); return true }
        if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }
        if (stack.last() != Screen.Home) { stack[0] = Screen.Home; return true }
        return false
    }

    fun setFull(on: Boolean) {
        fullscreen = on
        platform.setFullscreen(on)
    }

    fun watch(v: Video, queue: List<Video> = emptyList()) {
        playback.open(v, queue)
        go(Screen.Watch(v.id))
    }

    fun share(title: String, url: String) {
        if (!platform.share(title, url)) toast(tr("Link copied"))
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }
}
