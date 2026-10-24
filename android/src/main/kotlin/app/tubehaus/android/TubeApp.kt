package app.tubehaus.android

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import app.tubehaus.data.Store
import app.tubehaus.ui.AppState
import app.tubehaus.ui.Platform
import app.tubehaus.ui.VideoPlayer
import java.io.File
import kotlin.math.roundToInt

class TubeApp : Application() {
    lateinit var platform: AndroidPlatform
        private set
    val state: AppState by lazy { AppState(platform, Store(platform.dataDir)) }

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this)
    }
}

val Context.tube: TubeApp get() = applicationContext as TubeApp

class AndroidPlatform(private val app: Application) : Platform {
    override val name = "android"
    override val dataDir: File = File(app.filesDir, "tubehaus")
    override val cacheDir: File = app.cacheDir
    override val touch = true
    val androidPlayer by lazy { AndroidPlayer(app) }
    override val player: VideoPlayer get() = androidPlayer

    /** set by the activity: runs file pickers and full screen, which need an Activity */
    var activity: MainActivity? = null

    override fun share(title: String, url: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { app.startActivity(chooser); true } catch (e: ActivityNotFoundException) { false }
    }

    override fun openExternal(url: String) {
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {}
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray, done: (Boolean) -> Unit) {
        activity?.saveFile(name, mime, bytes, done) ?: done(false)
    }

    override fun openTextFile(done: (String?) -> Unit) {
        activity?.openTextFile(done) ?: done(null)
    }

    override fun setImmersive(on: Boolean) {
        activity?.setHideBars(on)
    }

    override fun setFullscreen(on: Boolean) {
        activity?.setFullscreen(on)
        // brightness set by the gesture lasts while in full screen; out of it the system's applies again
        if (!on) activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }

    private val audio by lazy { app.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** this window's brightness; until the gesture sets one it follows the system setting */
    override fun brightness(): Float? {
        val w = activity?.window ?: return null
        val own = w.attributes.screenBrightness
        if (own >= 0) return own
        return runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    override fun setBrightness(v: Float) {
        val w = activity?.window ?: return
        w.attributes = w.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }

    override fun mediaVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setMediaVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).roundToInt(), 0)
    }

    override fun metered(): Boolean =
        (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}
