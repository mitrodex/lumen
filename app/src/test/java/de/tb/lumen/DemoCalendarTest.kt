package de.tb.lumen

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** Die Demo-Kalender (docs/demo, für Screenshots und zum Ausprobieren) müssen jede Woche Termine liefern. */
class DemoCalendarTest {
    private val dir = listOf(File("../docs/demo"), File("docs/demo")).first { it.isDirectory }

    @Test
    fun everyDemoCalendarFillsAnyWeek() {
        val files = dir.listFiles { f -> f.extension == "ics" }.orEmpty()
        assertTrue("no demo calendars found", files.isNotEmpty())
        val zone = ZoneId.of("Europe/Berlin")
        // Irgendeine Woche weit in der Zukunft.
        val from = ZonedDateTime.of(2031, 3, 3, 0, 0, 0, 0, zone)
        for (f in files) {
            val events = Ics.parse(f.readText(), from, from.plusWeeks(1), 0)
            assertTrue("${f.name}: no events", events.isNotEmpty())
        }
    }
}
