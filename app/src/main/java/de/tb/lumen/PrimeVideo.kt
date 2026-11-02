package de.tb.lumen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

/**
 * Prime Video meldet beim Abspielen nur die Inhalts-ID (MEDIA_ID „amzn1.dv.gti…“), keinen Titel.
 * Titel und Bild stehen im Kopf der öffentlichen Detailseite – davon lesen wir nur die ersten
 * Kilobytes. Netzwerk: nur aus einem Hintergrund-Thread aufrufen.
 */
object PrimeVideo {
    const val PACKAGE = "com.amazon.firebat"
    private const val HEAD_BYTES = 64 * 1024

    data class Title(val name: String, val season: Int?, val image: Bitmap?)

    fun isId(id: String?) = id != null && id.startsWith("amzn1.dv.gti.")

    fun lookup(gti: String): Title? {
        val head = fetchHead("https://www.primevideo.com/-/de/detail/$gti") ?: return null
        val raw = Regex("<title>([^<]*)</title>").find(head)?.groupValues?.get(1)?.let(::unescape) ?: return null
        val title = parsePrimeTitle(raw) ?: return null
        val imageUrl = Regex("property=\"og:image\" content=\"([^\"]+)\"").find(head)?.groupValues?.get(1)
        return Title(title.name, title.season, imageUrl?.let(::image))
    }

    private fun fetchHead(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Accept-Language", "de-DE")
        try {
            conn.inputStream.use { input ->
                val buf = ByteArray(HEAD_BYTES)
                var n = 0
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun image(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            // Die Bilder sind groß (Full HD) – beim Dekodieren gleich verkleinern.
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
}
