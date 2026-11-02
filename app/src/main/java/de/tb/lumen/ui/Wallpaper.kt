package de.tb.lumen.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** Tageszeit für den Hintergrund. */
enum class DayPhase { Morning, Day, Evening, Night }

private class Blob(val x: Float, val y: Float, val r: Float, val color: Long)

private class Palette(val base: Long, val blobs: List<Blob>)

/**
 * Bunter, weicher Verlaufs-Hintergrund, je nach Tageszeit in anderen Farben. Wird einmal in
 * kleiner Auflösung gerendert und hochskaliert (bilinear = weich) – keine Echtzeit-Unschärfe.
 * Die dunkle Variante nutzt dieselben Farbflecken, nur gedämpft auf fast schwarzem Grund.
 */
object Wallpaper {
    private const val W = 384
    private const val H = 216

    private val palettes = mapOf(
        // Morgen: Pfirsich, Rosé, Gold
        DayPhase.Morning to Palette(
            0xFF1A1520, listOf(
                Blob(0.12f, 0.15f, 0.60f, 0xE6FFB38A),
                Blob(0.90f, 0.20f, 0.55f, 0xD9FF7AA2),
                Blob(0.62f, 1.00f, 0.60f, 0xD9FFD27A),
                Blob(0.02f, 1.05f, 0.40f, 0xB39D8CFF),
            ),
        ),
        // Tag: Koralle, Blau, Türkis
        DayPhase.Day to Palette(
            0xFF141826, listOf(
                Blob(0.12f, 0.15f, 0.60f, 0xE6FF7A6B),
                Blob(0.90f, 0.20f, 0.55f, 0xD94F7BFF),
                Blob(0.62f, 1.00f, 0.60f, 0xD92EC4C0),
                Blob(0.02f, 1.05f, 0.40f, 0xB38A5CFF),
            ),
        ),
        // Abend: Orange, Magenta, Violett
        DayPhase.Evening to Palette(
            0xFF160F1F, listOf(
                Blob(0.12f, 0.15f, 0.60f, 0xE6FF8A4C),
                Blob(0.90f, 0.20f, 0.55f, 0xD9E04E9B),
                Blob(0.62f, 1.00f, 0.60f, 0xD96A4CFF),
                Blob(0.02f, 1.05f, 0.40f, 0xB3C2364F),
            ),
        ),
        // Nacht: Indigo, Blau, Petrol
        DayPhase.Night to Palette(
            0xFF070A16, listOf(
                Blob(0.12f, 0.15f, 0.60f, 0xD93B4CCA),
                Blob(0.90f, 0.20f, 0.55f, 0xCC2F6BD9),
                Blob(0.62f, 1.00f, 0.60f, 0xB3207A8A),
                Blob(0.02f, 1.05f, 0.40f, 0x995A3FB0),
            ),
        ),
    )

    private val bitmaps = HashMap<Pair<DayPhase, Boolean>, Bitmap>()
    private val images = HashMap<Pair<DayPhase, Boolean>, ImageBitmap>()

    fun phase(ms: Long = System.currentTimeMillis()): DayPhase =
        when (Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour) {
            in 6..10 -> DayPhase.Morning
            in 11..16 -> DayPhase.Day
            in 17..20 -> DayPhase.Evening
            else -> DayPhase.Night
        }

    @Synchronized
    fun bitmap(phase: DayPhase, dark: Boolean): Bitmap = bitmaps.getOrPut(phase to dark) {
        Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { paint(Canvas(it), W, H, phase, dark, 0f) }
    }

    @Synchronized
    fun get(phase: DayPhase, dark: Boolean): ImageBitmap =
        images.getOrPut(phase to dark) { bitmap(phase, dark).asImageBitmap() }

    /**
     * Malt den Hintergrund. [drift] (Sekunden) lässt die Farbflecken langsam wandern –
     * 0 = Ruheposition (Startseite), > 0 für den Bildschirmschoner.
     */
    fun paint(c: Canvas, w: Int, h: Int, phase: DayPhase, dark: Boolean, drift: Float) {
        val palette = palettes.getValue(phase)
        c.drawColor(if (dark) 0xFF05060B.toInt() else palette.base.toInt())
        val strength = if (dark) 0.42f else 1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        palette.blobs.forEachIndexed { i, b ->
            val argb = b.color.toInt()
            val a = ((argb ushr 24) * strength).toInt()
            val inner = (a shl 24) or (argb and 0x00FFFFFF)
            // Jede Farbe auf eigener, langsamer und weiter Bahn quer durchs Bild.
            val speed = 0.06f + 0.02f * i
            val move = if (drift == 0f) 0f else 1f
            val cx = (b.x + move * (0.42f * sin(drift * speed + i * 1.7f) + (0.5f - b.x) * 0.5f)) * w
            val cy = (b.y + move * (0.38f * cos(drift * speed * 0.8f + i * 2.3f) + (0.5f - b.y) * 0.4f)) * h
            val r = b.r * w
            paint.shader = RadialGradient(cx, cy, r, intArrayOf(inner, argb and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, paint)
        }

        // Unten abdunkeln, damit Dock und Kacheln gut lesbar bleiben.
        paint.shader = LinearGradient(
            0f, h * 0.45f, 0f, h.toFloat(),
            0x00000000, if (dark) 0x8C000000.toInt() else 0x59000000, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }
}
