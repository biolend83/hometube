package app.tubehaus.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.tubehaus.data.Store
import app.tubehaus.ui.App
import app.tubehaus.ui.AppState
import app.tubehaus.ui.Platform
import app.tubehaus.ui.Screen
import app.tubehaus.ui.playerKey
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

class DesktopPlatform : Platform {
    override val name = "desktop"
    override val dataDir: File = File(System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/.config"), "TubeHaus")
    override val cacheDir: File = File(System.getenv("LOCALAPPDATA") ?: (System.getProperty("user.home") + "/.cache"), "TubeHaus")
    override val touch = false
    val mpv = MpvPlayer()
    override val player get() = mpv
    var window: WindowState? = null
    var frame: Frame? = null

    override fun share(title: String, url: String): Boolean {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        return false
    }

    override fun openExternal(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray, done: (Boolean) -> Unit) {
        val d = FileDialog(frame, "Save", FileDialog.SAVE).apply { file = name; isVisible = true }
        val f = d.file ?: return done(false)
        done(runCatching { File(d.directory, f).writeBytes(bytes) }.isSuccess)
    }

    override fun openTextFile(done: (String?) -> Unit) {
        val d = FileDialog(frame, "Open", FileDialog.LOAD).apply { isVisible = true }
        val f = d.file ?: return done(null)
        done(runCatching { File(d.directory, f).readText() }.getOrNull())
    }

    override fun setFullscreen(on: Boolean) {
        window?.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }
}

fun main() {
    // libmpv-2.dll: next to the installed app, or in desktop/resources/windows when run from Gradle
    System.getProperty("compose.application.resources.dir")?.let { System.setProperty("jna.library.path", it) }
        ?: System.setProperty("jna.library.path", File("resources/windows").absolutePath)
    val platform = DesktopPlatform()
    val state = AppState(platform, Store(platform.dataDir))
    application {
        val ws = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        platform.window = ws
        Window(
            onCloseRequest = {
                state.playback.close()
                state.store.flush()
                platform.mpv.shutdown()
                exitApplication()
            },
            state = ws,
            title = "TubeHaus",
            icon = androidx.compose.ui.res.painterResource("icon.png"),
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.Escape -> state.back()
                    Key.F11 -> { state.setFull(!state.fullscreen); true }
                    // the player's keys (space, arrows, J/K/L, F, M) where a video is on screen
                    else -> when {
                        state.current is Screen.Shorts -> state.shortsKeys?.invoke(e.key) ?: false
                        state.fullscreen || state.current is Screen.Watch -> playerKey(state, e.key)
                        else -> false
                    }
                }
            },
        ) {
            platform.frame = window
            App(state)
        }
    }
}
