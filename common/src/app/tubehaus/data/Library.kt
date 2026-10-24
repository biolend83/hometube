package app.tubehaus.data

import app.tubehaus.engine.Video
import kotlinx.serialization.Serializable

// The whole library lives in one JSON file on this device. Its shape matches the TubeHaus
// server's ytlib.json, so a "tubehaus-backup" file moves between the app and any TubeHaus
// server (or its public instances) unchanged.

@Serializable
data class Meta(
    val id: String,
    val title: String = "",
    val author: String = "",
    val authorId: String = "",
    val len: Int = 0,
    val published: Long = 0,
    val views: Long = 0,
    val live: Boolean = false,
    /** when it was watched (history only), epoch seconds */
    val at: Long = 0,
) {
    fun video() = Video(id, title, author, authorId, len, views = views, publishedAt = published, live = live)
}

fun Video.meta(at: Long = 0) = Meta(id, title, author, channelId, lengthSec, publishedAt, views, live, at)

@Serializable
data class Sub(val name: String = "", val thumb: String = "", val at: Long = 0)

@Serializable
data class PlaylistData(val title: String, val items: List<Meta> = emptyList(), val updated: Long = 0)

@Serializable
data class Library(
    val subs: Map<String, Sub> = emptyMap(),
    val hist: List<Meta> = emptyList(),
    /** id -> [seconds, length, when] */
    val pos: Map<String, List<Long>> = emptyMap(),
    val wl: List<Meta> = emptyList(),
    val likes: List<Meta> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val playlists: Map<String, PlaylistData> = emptyMap(),
)

/** What the recommenders learned. Everything here can be seen and wiped in Settings. */
@Serializable
data class Taste(
    /** channel id -> affinity, about -10..10 */
    val ch: Map<String, Double> = emptyMap(),
    /** keyword -> affinity */
    val kw: Map<String, Double> = emptyMap(),
    /** videos already shown as Shorts, id -> when */
    val seen: Map<String, Long> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val decayed: Long = 0,
    /** related videos of recently watched ones, the home feed's main candidates */
    val related: Map<String, List<Meta>> = emptyMap(),
    /** "not interested": hidden videos and channels */
    val hiddenVideos: List<String> = emptyList(),
    val hiddenChannels: List<String> = emptyList(),
)

@Serializable
data class Settings(
    /** interface language, "" until chosen (then English is used) */
    val lang: String = "",
    val region: String = "",
    /** highest video height to play, 0 = no limit */
    val maxHeight: Int = 1080,
    val maxHeightMobileData: Int = 480,
    val autoplay: Boolean = true,
    val captions: Boolean = false,
    val captionLang: String = "",
    val speed: Float = 1f,
    val backgroundPlay: Boolean = true,
    /** calm mode: no view counts, no Shorts tab, no autoplay of the next video */
    val calm: Boolean = false,
    val dark: Int = 0,            // 0 follow system, 1 light, 2 dark
    val historyOff: Boolean = false,
    /** the desktop player's own volume, 0..1 */
    val volume: Float = 1f,
    /** the first-start screen that offers to bring subscriptions over has been seen */
    val welcomed: Boolean = false,
)
