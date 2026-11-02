package de.tb.lumen

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Dunkelmodus für helle App-Banner (Netflix, YouTube …), ohne weiße Säume an den Kanten.
 *
 * 1. Hintergrund = alles, was vom Rand aus erreichbar ist, ohne kräftige Farbe zu kreuzen
 *    (Flutfüllung). Schwarze Schrift samt Buchstaben-Innenräumen gehört dazu; weiße Flächen,
 *    die von Farbe umschlossen sind (YouTube-Dreieck im roten Knopf), bleiben weiß.
 * 2. Im Hintergrund wird der Weißanteil jedes Pixels durch Dunkelgrau ersetzt. So werden auch
 *    die geglätteten Kanten (z. B. blassrosa zwischen Rot und Weiß) sauber dunkel statt hell.
 *    Farblose Pixel (Weiß, Grau, schwarze Schrift) werden in der Helligkeit umgekehrt.
 */
object BannerTint {
    private const val DARK_BG = 0x1C // ≈ #1C1C1E
    private const val LIGHT_FG = 0xF2

    /** null, wenn der Banner keinen hellen, farblosen Rand hat (dann nichts ändern). */
    fun darkVariant(src: Bitmap): Bitmap? {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        if (!hasLightBorder(px, w, h)) return null

        val background = backgroundMask(px, w, h)
        addTextCounters(px, w, h, background)
        for (i in px.indices) {
            val c = px[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            // 1 = grau/weiß/schwarz, 0 = kräftige Farbe.
            val neutral = (1f - (max - min) / 60f).coerceIn(0f, 1f)
            val light = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            val inv = (LIGHT_FG - (LIGHT_FG - DARK_BG) * light).toInt()

            px[i] = if (background[i] && light < 0.42f) {
                // Dunkle Schrift (schwarz, aber auch dunkel getönt wie „Proton“): aufhellen und
                // dabei den Farbton behalten – sonst würde sie auf dem dunklen Grund verschwinden.
                val t = ((0.42f - light) / 0.42f).coerceIn(0f, 1f) * 0.88f + 0.08f
                Color.argb(Color.alpha(c), mix(r, LIGHT_FG, t), mix(g, LIGHT_FG, t), mix(b, LIGHT_FG, t))
            } else if (background[i]) {
                // Weißanteil (= Minimum der Kanäle) durch Dunkelgrau ersetzen.
                val white = min / 255f
                val shift = white * (255 - DARK_BG)
                val rr = mix((r - shift).toInt(), inv, neutral)
                val gg = mix((g - shift).toInt(), inv, neutral)
                val bb = mix((b - shift).toInt(), inv, neutral)
                Color.argb(Color.alpha(c), rr, gg, bb)
            } else {
                // Von Farbe umschlossen (Logo-Innenleben) oder selbst kräftig farbig: unverändert.
                c
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun mix(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)

    /**
     * Sperre für die Flutfüllung: kräftige, nicht aufgehellte Farbe (z. B. YouTube-Rot).
     * Geglättete Kanten zu Weiß (blassrosa) sind aufgehellt und gehören zum Hintergrund.
     */
    private fun isBarrier(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        val min = minOf(r, g, b)
        return maxOf(r, g, b) - min >= 60 && min < 90
    }

    /** Flutfüllung vom Rand, gestoppt nur von kräftiger Farbe. */
    private fun backgroundMask(px: IntArray, w: Int, h: Int): BooleanArray {
        val mask = BooleanArray(px.size)
        val queue = IntArray(px.size)
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (!mask[i] && !isBarrier(px[i])) {
                mask[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) {
            push(x)
            push((h - 1) * w + x)
        }
        for (y in 0 until h) {
            push(y * w)
            push(y * w + w - 1)
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return mask
    }

    /**
     * Innenräume von Buchstaben (P, o, e …): eingeschlossene weiße Flächen, die die Flutfüllung
     * nicht erreicht, weil die geglättete Buchstabenkante sie abschottet. Liegt rund um so eine
     * Fläche überwiegend dunkle Schrift, gehört sie zum Hintergrund. Weiße Flächen in Farbe
     * (YouTube-Dreieck im roten Knopf) bleiben dagegen weiß.
     */
    private fun addTextCounters(px: IntArray, w: Int, h: Int, mask: BooleanArray) {
        fun whiteish(c: Int): Boolean {
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            return minOf(r, g, b) > 200 && maxOf(r, g, b) - minOf(r, g, b) < 30
        }
        fun lum(c: Int) = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) / 255f
        val seen = BooleanArray(px.size)
        val queue = IntArray(px.size)
        for (start in px.indices) {
            if (seen[start] || mask[start] || !whiteish(px[start])) continue
            // Fläche einsammeln
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                    if (n >= 0 && !seen[n] && !mask[n] && whiteish(px[n])) {
                        seen[n] = true
                        queue[tail++] = n
                    }
                }
            }
            // Umgebung prüfen: 3 Pixel nach außen in alle Richtungen von jedem Randpixel.
            var ink = 0
            var colored = 0
            for (k in 0 until tail) {
                val i = queue[k]
                val x = i % w
                val y = i / w
                for ((dx, dy) in arrayOf(3 to 0, -3 to 0, 0 to 3, 0 to -3)) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val c = px[ny * w + nx]
                    if (whiteish(c)) continue
                    val chroma = maxOf(Color.red(c), Color.green(c), Color.blue(c)) - minOf(Color.red(c), Color.green(c), Color.blue(c))
                    if (lum(c) < 0.42f && chroma < 110) ink++ else colored++
                }
            }
            // Farbige Buchstaben („VPN“): Strich ist dünn – ein Stück weiter außen liegt wieder
            // Hintergrund. Beim YouTube-Knopf ist dort noch Rot → Dreieck bleibt weiß.
            val probe = (w / 28).coerceAtLeast(6)
            var outside = 0
            var inside = 0
            for (k in 0 until tail step 2) {
                val i = queue[k]
                val x = i % w
                val y = i / w
                for ((dx, dy) in arrayOf(probe to 0, -probe to 0, 0 to probe, 0 to -probe)) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val n = ny * w + nx
                    if (seen[n] && !mask[n]) continue // noch in derselben Fläche
                    if (mask[n]) outside++ else inside++
                }
            }
            val thinStroke = outside > inside
            if (ink > colored * 2 || thinStroke) {
                for (k in 0 until tail) mask[queue[k]] = true
                // Auch den hellen Kantensaum im Innenraum mitnehmen (sonst bleibt ein lila Ring).
                growMask(px, w, h, mask, IntArray(tail) { queue[it] })
            }
        }
    }

    /** Flutfüllung ab [seeds] über alles, was keine kräftige Farbe ist (wie vom Bildrand aus). */
    private fun growMask(px: IntArray, w: Int, h: Int, mask: BooleanArray, seeds: IntArray) {
        val queue = IntArray(px.size)
        var head = 0
        var tail = 0
        seeds.forEach { queue[tail++] = it }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                if (n >= 0 && !mask[n] && !isBarrier(px[n])) {
                    mask[n] = true
                    queue[tail++] = n
                }
            }
        }
    }

    /** Rand überwiegend hell und farblos? */
    private fun hasLightBorder(px: IntArray, w: Int, h: Int): Boolean {
        var light = 0
        var total = 0
        fun sample(x: Int, y: Int) {
            val c = px[y * w + x]
            val max = maxOf(Color.red(c), Color.green(c), Color.blue(c))
            val min = minOf(Color.red(c), Color.green(c), Color.blue(c))
            total++
            if (min > 215 && max - min < 25) light++
        }
        for (x in 0 until w step 4) {
            sample(x, 2)
            sample(x, h - 3)
        }
        for (y in 0 until h step 4) {
            sample(2, y)
            sample(w - 3, y)
        }
        return light > total * 0.8f
    }
}
