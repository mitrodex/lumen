package de.tb.lumen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Ein Termin (Ganztägig: [start]/[end] sind Mitternacht lokaler Zeit). */
data class CalEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean, val color: Int)

/**
 * Wochenkalender aus veröffentlichten iCloud-Kalendern (iPhone: Kalender → (i) → „Öffentlicher
 * Kalender“ → Link teilen) oder jedem anderen ICS-Link. Lumen liest die Dateien ohne Konto – alle
 * 5 Minuten und bei jeder Rückkehr auf die Startseite, Ergebnis als Datei zwischengespeichert.
 * Links kommen aus [Config.calendarUrls]. Netzwerk: nur aus einem Hintergrund-Thread aufrufen.
 */
object Calendar {
    private const val MAX_AGE_MS = 5 * 60_000L

    /** Beim Zurückkehren auf die Startseite – aber nicht öfter als alle 30 Sekunden. */
    const val ON_RETURN_MS = 30_000L

    /** So viele Wochen nach dieser werden geladen (und lassen sich auf der Startseite anzeigen). */
    const val WEEKS_AHEAD = 3
    private const val DEFAULT_COLOR = 0xFF0A84FF.toInt()

    fun configured(context: Context) = Config.calendarUrls(context).isNotEmpty()

    private fun cacheFile(context: Context) = File(context.filesDir, "calendar.json")

    /** Montag 0:00 dieser Woche bis Montag 0:00 der nächsten. */
    fun week(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<ZonedDateTime, ZonedDateTime> {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone)
        return monday to monday.plusWeeks(1)
    }

    fun cached(context: Context): List<CalEvent> = runCatching {
        val a = JSONArray(cacheFile(context).readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            CalEvent(o.getString("t"), o.getLong("s"), o.getLong("e"), o.getBoolean("a"), o.getInt("c"))
        }
    }.getOrDefault(emptyList())

    /** Zwischenstand verwerfen (z. B. nach geänderten Links) – der nächste Abruf lädt alles neu. */
    fun invalidate(context: Context) {
        cacheFile(context).delete()
    }

    /** Lädt neu, wenn der Stand älter als [maxAgeMs] ist (Standard 5 Minuten); null = nichts Neues. */
    fun refreshIfDue(context: Context, maxAgeMs: Long = MAX_AGE_MS): List<CalEvent>? {
        val urls = Config.calendarUrls(context)
        if (urls.isEmpty()) return null
        val file = cacheFile(context)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs) return null
        // Vier Wochen: auf der Startseite lässt sich so weit vorblättern, der Bildschirmschoner zeigt die nächsten.
        val (from, weekEnd) = week(System.currentTimeMillis())
        val to = weekEnd.plusWeeks(WEEKS_AHEAD.toLong())
        // Fällt ein Kalender aus, bleibt der alte Stand stehen statt leer zu werden.
        val texts = urls.map { fetch(it) ?: return null }
        val events = texts.flatMap { Ics.parse(it, from, to, DEFAULT_COLOR) }.sortedBy { it.start }
        val a = JSONArray()
        events.forEach { a.put(JSONObject().put("t", it.title).put("s", it.start).put("e", it.end).put("a", it.allDay).put("c", it.color)) }
        runCatching { file.writeText(a.toString()) }
        return events
    }

    private fun fetch(url: String): String? = runCatching {
        val conn = URL(url.replaceFirst("webcal://", "https://")).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
