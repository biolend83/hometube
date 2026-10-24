package app.tubehaus.desktop

import app.tubehaus.engine.Net
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * A tiny HTTP server on 127.0.0.1 between mpv and YouTube. mpv asks for a stream from where it
 * is to the end in one request, and YouTube serves requests like that at a crawl (a second of
 * video took ten); asked in 10 MB ranges it sends at full speed. So mpv talks to this, and this
 * fetches the stream in 10 MB pieces and hands them on as one response. Seeking is just a new
 * request with another start, as mpv already does it.
 */
object LocalProxy {
    private const val CHUNK = 10L shl 20

    private val urls = ConcurrentHashMap<String, String>()

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16).apply {
            createContext("/") { x -> handle(x) }
            executor = Executors.newCachedThreadPool { r -> Thread(r, "stream-proxy").apply { isDaemon = true } }
            start()
        }
    }

    /** the address mpv should open instead of [url] */
    fun wrap(url: String): String {
        val key = Integer.toHexString(url.hashCode()) + Integer.toHexString(url.length)
        urls[key] = url
        return "http://127.0.0.1:${server.address.port}/$key"
    }

    private fun fetch(url: String, from: Long, to: Long): Response {
        var last: Exception? = null
        repeat(3) {
            try {
                val r = Net.client.newCall(Request.Builder().url(url).header("Range", "bytes=$from-$to").build()).execute()
                if (r.code == 206 || r.code == 200) return r
                r.close()
                last = IOException("HTTP ${r.code}")
                if (r.code == 403 || r.code == 404 || r.code == 416) throw last!!
            } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("fetch failed")
    }

    private fun handle(x: HttpExchange) {
        try {
            val url = urls[x.requestURI.path.trimStart('/')] ?: return x.sendResponseHeaders(404, -1)
            // "bytes=a-" or "bytes=a-b"; none means the whole stream
            val range = x.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.split('-')
            val from = range?.getOrNull(0)?.toLongOrNull() ?: 0L
            val wantTo = range?.getOrNull(1)?.toLongOrNull()

            var piece = fetch(url, from, from + CHUNK - 1)
            val total = piece.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: piece.body!!.contentLength().takeIf { it >= 0 }?.plus(from)
            val end = min(wantTo ?: Long.MAX_VALUE, (total ?: Long.MAX_VALUE) - 1)
            x.responseHeaders.add("Content-Type", piece.header("Content-Type") ?: "application/octet-stream")
            x.responseHeaders.add("Accept-Ranges", "bytes")
            if (range != null && total != null) x.responseHeaders.add("Content-Range", "bytes $from-$end/$total")
            val length = if (total != null) end - from + 1 else 0L      // 0: length unknown, chunked
            if (x.requestMethod == "HEAD") { piece.close(); x.sendResponseHeaders(if (range != null) 206 else 200, -1); return }
            x.sendResponseHeaders(if (range != null) 206 else 200, length)

            x.responseBody.use { out ->
                var pos = from
                val buf = ByteArray(256 * 1024)
                while (true) {
                    piece.body!!.byteStream().use { inp ->
                        while (pos <= end) {
                            val n = inp.read(buf, 0, min(buf.size.toLong(), end - pos + 1).toInt())
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                        }
                    }
                    piece.close()
                    if (pos > end || total == null) break
                    piece = fetch(url, pos, min(pos + CHUNK - 1, end))
                }
            }
        } catch (_: IOException) {
            // mpv closed the connection (a seek, another quality, the next video): nothing to do
        } finally {
            x.close()
        }
    }
}
