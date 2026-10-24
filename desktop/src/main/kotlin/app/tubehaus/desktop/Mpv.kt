package app.tubehaus.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.PointerByReference

/**
 * The few libmpv functions the player needs (libmpv-2.dll): the client API (client.h) and the
 * render API (render.h), which hands each video frame to the app instead of opening a window.
 * Handles are opaque pointers, strings are UTF-8.
 */
@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: DoubleByReference): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_free(data: Pointer)

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    /** called on one of mpv's threads when a new frame is due; must not call back into mpv */
    fun interface UpdateCallback : Callback {
        fun invoke(data: Pointer?)
    }

    companion object {
        const val FORMAT_DOUBLE = 5
        const val EVENT_SHUTDOWN = 1
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_ERROR = 4

        // render.h
        const val RENDER_PARAM_INVALID = 0
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20
        const val RENDER_UPDATE_FRAME = 1L

        fun load(): LibMpv? = try {
            Native.load("mpv-2", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        } catch (e: UnsatisfiedLinkError) {
            try { Native.load("mpv", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")) } catch (e2: UnsatisfiedLinkError) { null }
        }

        /** a mpv_render_param array ({int type; void *data} each, ended by INVALID); the caller keeps the data alive */
        fun params(vararg p: Pair<Int, Pointer?>): Memory {
            val m = Memory(16L * (p.size + 1))
            m.clear()
            p.forEachIndexed { i, (type, data) ->
                m.setInt(16L * i, type)
                m.setPointer(16L * i + 8, data)
            }
            m.setInt(16L * p.size, RENDER_PARAM_INVALID)
            return m
        }

        fun cString(s: String): Memory {
            val b = s.toByteArray()
            return Memory(b.size + 1L).apply { write(0, b, 0, b.size); setByte(b.size.toLong(), 0) }
        }
    }
}
