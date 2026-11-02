package de.tb.lumen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.View
import de.tb.lumen.ui.Wallpaper
import de.tb.lumen.ui.formatDate
import de.tb.lumen.ui.formatTime
import kotlin.math.sin

/**
 * Bildschirmschoner: die Lumen-Farbflächen der aktuellen Tageszeit wandern langsam, dazu Uhr,
 * Datum und Wetter. Aktivieren per ADB:
 *   adb shell settings put secure screensaver_components de.tb.lumen/.LumenDream
 */
class LumenDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(DreamView(this))
    }
}

private class DreamView(context: Context) : View(context) {
    // Klein rendern, hochskaliert zeichnen: weich und billig.
    private val small = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888)
    private val smallCanvas = Canvas(small)
    private val src = Rect(0, 0, small.width, small.height)
    private val dst = Rect()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val start = SystemClock.elapsedRealtime()

    private val timePaint = textPaint(96f, "sans-serif-thin", 255)
    private val datePaint = textPaint(22f, "sans-serif-medium", 215)
    private val weatherPaint = textPaint(20f, "sans-serif-medium", 180)

    private fun textPaint(sp: Float, family: String, alpha: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha, 255, 255, 255)
        typeface = Typeface.create(family, Typeface.NORMAL)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        setShadowLayer(14f, 0f, 1f, Color.argb(50, 0, 0, 0))
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val seconds = (SystemClock.elapsedRealtime() - start) / 1000f
        val dark = Appearance.isDark(context)
        Wallpaper.paint(smallCanvas, small.width, small.height, Wallpaper.phase(now), dark, drift = seconds + 1f)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(small, src, dst, bitmapPaint)

        // Schrift wandert minütlich ein Stück (Einbrennschutz).
        val minute = now / 60_000
        val ox = dp(48f) + dp(40f) * (0.5f + 0.5f * sin(minute * 0.9f))
        val oy = height - dp(56f) - dp(30f) * (0.5f + 0.5f * sin(minute * 1.3f))

        var y = oy
        Weather.cached(context)?.let { w ->
            canvas.drawText("${w.tempC}°  ${Weather.describe(w.code, w.isDay)}", ox + dp(4f), y, weatherPaint)
            y -= dp(34f)
        }
        canvas.drawText(formatDate(now), ox + dp(4f), y, datePaint)
        y -= dp(30f)
        canvas.drawText(formatTime(now), ox, y, timePaint)

        drawUpcoming(canvas, now, minute)

        // ~20 Bilder/s reichen für die langsame Bewegung und schonen den Stick.
        postInvalidateDelayed(50)
    }

    // Termine nur einmal pro Minute aus dem Zwischenspeicher lesen, nicht bei jedem Bild.
    private var events: List<CalEvent> = emptyList()
    private var eventsMinute = -1L
    private val headPaint = textPaint(13f, "sans-serif-medium", 170).apply { letterSpacing = 0.12f }
    private val eventPaint = textPaint(20f, "sans-serif-medium", 225)
    private val whenPaint = textPaint(16f, "sans-serif", 170)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Rechts unten: die nächsten Termine (Kalender der Startseite), wandert gegenläufig zur Uhr. */
    private fun drawUpcoming(canvas: Canvas, now: Long, minute: Long) {
        if (minute != eventsMinute) {
            eventsMinute = minute
            events = Calendar.cached(context).filter { it.end > now }.sortedBy { it.start }.take(UPCOMING)
        }
        if (events.isEmpty()) return
        val right = width - dp(48f) - dp(40f) * (0.5f + 0.5f * sin(minute * 0.7f))
        val line = dp(34f)
        var y = height - dp(56f) - dp(30f) * (0.5f + 0.5f * sin(minute * 1.1f)) - line * (events.size - 1)
        // Block rechts, Inhalt linksbündig: Punkt | Zeit (feste Spalte) | Titel.
        val head = "NÄCHSTE TERMINE"
        val times = events.map { whenText(it, now) }
        val timeCol = times.maxOf { whenPaint.measureText(it) }
        val titleX = dp(16f) + timeCol + dp(12f)
        val blockW = maxOf(headPaint.measureText(head), titleX + events.maxOf { eventPaint.measureText(it.title) })
        val left = right - blockW
        canvas.drawText(head, left, y - dp(34f), headPaint)
        events.forEachIndexed { i, e ->
            dotPaint.color = e.color
            canvas.drawCircle(left + dp(4.5f), y - dp(6f), dp(4.5f), dotPaint)
            canvas.drawText(times[i], left + dp(16f), y, whenPaint)
            canvas.drawText(e.title, left + titleX, y, eventPaint)
            y += line
        }
    }

    private fun whenText(e: CalEvent, now: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = java.time.Instant.ofEpochMilli(e.start).atZone(zone)
        val day = when (val d = java.time.temporal.ChronoUnit.DAYS.between(today, start.toLocalDate())) {
            in Long.MIN_VALUE..0L -> if (e.start <= now) "Jetzt" else "Heute"
            1L -> "Morgen"
            else -> start.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN).removeSuffix(".") +
                if (d >= 7) " ${start.dayOfMonth}." else ""
        }
        if (day == "Jetzt" || e.allDay) return day
        return "$day ${start.hour}:%02d".format(start.minute)
    }

    private companion object {
        const val UPCOMING = 4
    }
}
