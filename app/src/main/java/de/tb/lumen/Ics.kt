package de.tb.lumen

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Kleiner ICS-Leser: VEVENTs samt einfacher Wiederholungen (RRULE), Ausnahmen und Verschiebungen. */
internal object Ics {
    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    fun parse(text: String, from: ZonedDateTime, to: ZonedDateTime, defaultColor: Int): List<CalEvent> {
        // Zeilen entfalten: Folgezeilen beginnen mit Leerzeichen/Tab.
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split('\n')
        val color = lines.firstOrNull { it.startsWith("X-APPLE-CALENDAR-COLOR") }
            ?.substringAfter(':')?.let(::parseColor) ?: defaultColor

        val events = mutableListOf<Map<String, Prop>>()
        var cur: MutableMap<String, Prop>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> cur = mutableMapOf()
                line == "END:VEVENT" -> { cur?.let(events::add); cur = null }
                cur != null && ':' in line -> {
                    val p = prop(line)
                    // EXDATE darf mehrfach vorkommen.
                    if (p.name == "EXDATE") cur["EXDATE#${cur.size}"] = p else cur[p.name] = p
                }
            }
        }

        // Verschobene Einzeltermine (RECURRENCE-ID) ersetzen das jeweilige Wiederholungs-Datum.
        val overridden = events.mapNotNull { e ->
            val rid = e["RECURRENCE-ID"] ?: return@mapNotNull null
            val uid = e["UID"]?.value ?: return@mapNotNull null
            uid to (time(rid)?.toEpochSecond() ?: return@mapNotNull null)
        }.toSet()

        val out = mutableListOf<CalEvent>()
        for (e in events) {
            val startProp = e["DTSTART"] ?: continue
            val start = time(startProp) ?: continue
            val allDay = startProp.params["VALUE"] == "DATE" || startProp.value.length == 8
            val end = e["DTEND"]?.let(::time)
                ?: e["DURATION"]?.let { d -> runCatching { start.plus(Duration.parse(d.value)) }.getOrNull() }
                ?: if (allDay) start.plusDays(1) else start
            val length = Duration.between(start, end)
            val title = unescape(e["SUMMARY"]?.value ?: "—")
            val uid = e["UID"]?.value
            val excluded = e.filterKeys { it.startsWith("EXDATE") }.values
                .flatMap { p -> p.value.split(',').mapNotNull { v -> time(Prop("EXDATE", p.params, v))?.toEpochSecond() } }
                .toSet()

            val starts = e["RRULE"]?.let { expand(start, it.value, from, to) } ?: sequenceOf(start)
            for (s in starts) {
                if (!s.isBefore(to)) break
                val en = s.plus(length)
                // Überschneidet die Woche (Termine ohne Dauer: Beginn liegt drin).
                if (if (en == s) s.isBefore(from) else !en.isAfter(from)) continue
                if (e["RRULE"] != null && (s.toEpochSecond() in excluded || (uid to s.toEpochSecond()) in overridden)) continue
                out += CalEvent(title, s.toInstant().toEpochMilli(), en.toInstant().toEpochMilli(), allDay, color)
            }
        }
        return out
    }

    /** Wiederholungen bis [to]: DAILY/WEEKLY (mit BYDAY)/MONTHLY/YEARLY, INTERVAL, COUNT, UNTIL. */
    private fun expand(start: ZonedDateTime, rule: String, from: ZonedDateTime, to: ZonedDateTime): Sequence<ZonedDateTime> = sequence {
        val r = rule.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
        val interval = r["INTERVAL"]?.toIntOrNull() ?: 1
        val count = r["COUNT"]?.toIntOrNull()
        // UNTIL als reines Datum gilt den ganzen Tag.
        val until = r["UNTIL"]?.let { u -> time(Prop("UNTIL", emptyMap(), u), start.zone)?.let { if (u.length == 8) it.plusDays(1) else it } }
        val unit = when (r["FREQ"]) {
            "DAILY" -> ChronoUnit.DAYS
            "WEEKLY" -> ChronoUnit.WEEKS
            "MONTHLY" -> ChronoUnit.MONTHS
            "YEARLY" -> ChronoUnit.YEARS
            else -> { yield(start); return@sequence }
        }
        val days = if (unit == ChronoUnit.WEEKS) r["BYDAY"]?.split(',')?.mapNotNull(::weekday) else null
        var n = 0
        // Ohne COUNT gleich bis kurz vor die Woche springen (alte Serien nicht ab Beginn durchzählen).
        var period = if (count == null) (unit.between(start, from) / interval - 1).coerceAtLeast(0) else 0L
        val firstPeriod = period
        while (true) {
            val base = start.plus(period * interval, unit)
            val candidates = if (days.isNullOrEmpty()) listOf(base) else {
                val monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                days.sorted().map { monday.with(TemporalAdjusters.nextOrSame(it)) }.filter { !it.isBefore(start) }
            }
            for (c in candidates) {
                if (until != null && c.isAfter(until)) return@sequence
                if (count != null && n >= count) return@sequence
                if (c.isAfter(to)) return@sequence
                n++
                yield(c)
            }
            period++
            if (period - firstPeriod > 5000) return@sequence
        }
    }

    private fun weekday(s: String): DayOfWeek? = when (s.takeLast(2)) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY; "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun prop(line: String): Prop {
        val head = line.substringBefore(':')
        val parts = head.split(';')
        val params = parts.drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=').trim('"') }
        return Prop(parts[0].uppercase(), params, line.substringAfter(':'))
    }

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun time(p: Prop, fallback: ZoneId = ZoneId.systemDefault()): ZonedDateTime? = runCatching {
        val v = p.value.trim()
        val local = ZoneId.systemDefault()
        when {
            v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay(local)
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC).withZoneSameInstant(local)
            else -> {
                val zone = p.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
                LocalDateTime.parse(v, DATE_TIME).atZone(zone).withZoneSameInstant(local)
            }
        }
    }.getOrNull()

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseColor(s: String): Int? = runCatching {
        val hex = s.trim().removePrefix("#").take(6)
        (0xFF000000 or hex.toLong(16)).toInt()
    }.getOrNull()
}
