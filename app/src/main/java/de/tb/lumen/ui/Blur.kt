package de.tb.lumen.ui

import android.graphics.Bitmap

/**
 * Einmalige Unschärfe für Hintergründe (Android 9 hat keinen Echtzeit-Blur): stark verkleinern,
 * dreimal Box-Blur (≈ Gauß) und später bilinear hochskaliert zeichnen. Kostet ein paar ms.
 */
object Blur {
    fun of(src: Bitmap, width: Int = 192, radius: Int = 5): Bitmap {
        val h = (width * src.height / src.width.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, width, h, true)
        val px = IntArray(width * h)
        small.getPixels(px, 0, width, 0, 0, width, h)
        val tmp = IntArray(px.size)
        repeat(3) {
            boxPass(px, tmp, width, h, radius, horizontal = true)
            boxPass(tmp, px, width, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, width, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            for (i in -r..r) {
                val c = at(i)
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            val n = 2 * r + 1
            for (i in 0 until len) {
                val out = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val add = at(i + r + 1)
                val sub = at(i - r)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
