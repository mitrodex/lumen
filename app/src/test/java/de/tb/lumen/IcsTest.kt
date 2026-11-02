package de.tb.lumen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class IcsTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // Woche Mo 2.11.2026 – Mo 9.11.2026.
    private val from = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, berlin)
    private val to = from.plusWeeks(1)

    @Before
    fun zone() {
        TimeZone.setDefault(TimeZone.getTimeZone(berlin))
    }

    private fun cal(vararg events: String, header: String = "") =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n$header" + events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun parse(text: String) = Ics.parse(text, from, to, DEFAULT)

    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(berlin)

    @Test
    fun simpleEventInsideWeek() {
        val events = parse(cal("UID:1\r\nSUMMARY:Zahnarzt\r\nDTSTART;TZID=Europe/Berlin:20261104T090000\r\nDTEND;TZID=Europe/Berlin:20261104T100000"))
        assertEquals(1, events.size)
        val e = events.single()
        assertEquals("Zahnarzt", e.title)
        assertEquals(9, local(e.start).hour)
        assertEquals(10, local(e.end).hour)
        assertEquals(false, e.allDay)
        assertEquals(DEFAULT, e.color)
    }

    @Test
    fun eventOutsideWeekIsDropped() {
        val events = parse(cal("UID:1\r\nSUMMARY:Später\r\nDTSTART:20261120T090000Z\r\nDTEND:20261120T100000Z"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun utcTimeIsConvertedToLocal() {
        val e = parse(cal("UID:1\r\nSUMMARY:UTC\r\nDTSTART:20261103T080000Z\r\nDTEND:20261103T090000Z")).single()
        // November = Winterzeit, UTC+1.
        assertEquals(9, local(e.start).hour)
    }

    @Test
    fun allDayEvent() {
        val e = parse(cal("UID:1\r\nSUMMARY:Urlaub\r\nDTSTART;VALUE=DATE:20261105\r\nDTEND;VALUE=DATE:20261106")).single()
        assertTrue(e.allDay)
        assertEquals(5, local(e.start).dayOfMonth)
        assertEquals(0, local(e.start).hour)
    }

    @Test
    fun weeklyRuleWithByDayAndExdate() {
        val events = parse(
            cal(
                "UID:r\r\nSUMMARY:Training\r\nDTSTART;TZID=Europe/Berlin:20260907T180000\r\nDTEND;TZID=Europe/Berlin:20260907T190000\r\n" +
                    "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR\r\nEXDATE;TZID=Europe/Berlin:20261104T180000",
            ),
        )
        // Mo 2., (Mi 4. ausgenommen), Fr 6.
        assertEquals(listOf(2, 6), events.map { local(it.start).dayOfMonth })
        assertTrue(events.all { local(it.start).hour == 18 })
    }

    @Test
    fun countLimitsRepetitions() {
        val events = parse(cal("UID:c\r\nSUMMARY:Kurs\r\nDTSTART;TZID=Europe/Berlin:20261101T100000\r\nDTEND;TZID=Europe/Berlin:20261101T110000\r\nRRULE:FREQ=DAILY;COUNT=3"))
        // 1.11. liegt vor der Woche, 2. und 3. darin, danach ist Schluss.
        assertEquals(listOf(2, 3), events.map { local(it.start).dayOfMonth })
    }

    @Test
    fun untilEndsRepetitions() {
        val events = parse(cal("UID:u\r\nSUMMARY:Bis Mittwoch\r\nDTSTART;TZID=Europe/Berlin:20261102T070000\r\nDTEND;TZID=Europe/Berlin:20261102T080000\r\nRRULE:FREQ=DAILY;UNTIL=20261104"))
        assertEquals(listOf(2, 3, 4), events.map { local(it.start).dayOfMonth })
    }

    @Test
    fun movedOccurrenceReplacesOriginal() {
        val events = parse(
            cal(
                "UID:m\r\nSUMMARY:Meeting\r\nDTSTART;TZID=Europe/Berlin:20261005T100000\r\nDTEND;TZID=Europe/Berlin:20261005T110000\r\nRRULE:FREQ=WEEKLY",
                "UID:m\r\nRECURRENCE-ID;TZID=Europe/Berlin:20261102T100000\r\nSUMMARY:Meeting (verschoben)\r\nDTSTART;TZID=Europe/Berlin:20261103T140000\r\nDTEND;TZID=Europe/Berlin:20261103T150000",
            ),
        )
        assertEquals(1, events.size)
        assertEquals("Meeting (verschoben)", events.single().title)
        assertEquals(3, local(events.single().start).dayOfMonth)
    }

    @Test
    fun foldedLinesEscapesAndCalendarColor() {
        val text = cal(
            "UID:f\r\nSUMMARY:Essen mit Anna\\, Ben\r\n  und Clara\r\nDTSTART;TZID=Europe/Berlin:20261106T190000\r\nDURATION:PT2H",
            header = "X-APPLE-CALENDAR-COLOR:#FF2968\r\n",
        )
        val e = parse(text).single()
        assertEquals("Essen mit Anna, Ben und Clara", e.title)
        assertEquals(21, local(e.end).hour)
        assertEquals(0xFFFF2968.toInt(), e.color)
    }

    @Test
    fun weekStartsOnMonday() {
        val thursday = ZonedDateTime.of(2026, 11, 5, 15, 0, 0, 0, berlin).toInstant().toEpochMilli()
        val (start, end) = Calendar.week(thursday, berlin)
        assertEquals(from, start)
        assertEquals(to, end)
    }

    private companion object {
        val DEFAULT = 0xFF0A84FF.toInt()
    }
}
