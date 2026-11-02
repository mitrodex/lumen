package de.tb.lumen.ui

import de.tb.lumen.L10n
import de.tb.lumen.R
import de.tb.lumen.str

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.CalEvent
import de.tb.lumen.Calendar
import de.tb.lumen.LauncherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_PER_DAY = 4
private val TIME = DateTimeFormatter.ofPattern("H:mm")

/**
 * Diese Woche (Mo–So) wie das iPad-Kalender-Widget: sieben Spalten, heute hervorgehoben,
 * je Tag die Termine mit Farbstreifen ihres Kalenders. Ohne Fokus eine flache Anzeige; angewählt
 * (Pfeil hoch aus den Favoriten) blättert links/rechts durch die Wochen und hoch/runter durch
 * alle Termine. Am oberen/unteren Ende geht es mit [onUp]/[onDown] hinaus.
 */
@Composable
fun WeekCalendar(
    model: LauncherModel,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
    onFocused: (Boolean) -> Unit = {},
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
) {
    val zone = ZoneId.systemDefault()
    var focused by remember { mutableStateOf(false) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 120.dp.toPx() }

    val (thisWeek, _) = Calendar.week(model.now, zone)
    val from = thisWeek.plusWeeks(weekOffset.toLong())
    val today = Instant.ofEpochMilli(model.now).atZone(zone).toLocalDate()
    val days = List(7) { from.toLocalDate().plusDays(it.toLong()) }
    val month = days[3].month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, L10n.locale)
    val heading = when (weekOffset) {
        0 -> str(R.string.cal_this_week)
        1 -> str(R.string.cal_next_week)
        else -> str(R.string.cal_in_weeks, weekOffset)
    }

    // Ohne Fokus bewusst „flacher“ als anwählbare Kacheln: kein leuchtender Rand, kein Glanz, Tage
    // ohne eigene Flächen. Angewählt: volles Glas mit hellem Rand.
    val glass = LocalGlass.current
    val passive = glass.copy(edgeTop = glass.edgeTop * 0.25f, edgeBottom = 0f, sheen = 0f, fill = glass.fill * 0.6f)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
                // Beim Verlassen zurück auf diese Woche, ganz oben.
                if (!it.isFocused) {
                    weekOffset = 0
                    scope.launch { scroll.scrollTo(0) }
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> {
                        if (scroll.value < scroll.maxValue) scope.launch { scroll.animateScrollBy(step) } else onDown()
                        true
                    }
                    Key.DirectionUp -> {
                        if (scroll.value > 0) scope.launch { scroll.animateScrollBy(-step) } else onUp()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val next = (weekOffset + if (e.key == Key.DirectionLeft) -1 else 1).coerceIn(0, Calendar.WEEKS_AHEAD)
                        // Links in dieser Woche: Fokus normal weitergeben (z. B. zum Musik-Player).
                        if (next == weekOffset) return@onPreviewKeyEvent e.key == Key.DirectionRight
                        weekOffset = next
                        scope.launch { scroll.scrollTo(0) }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .glass(shape, if (focused) glass else passive)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.55f), shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (focused) LText(if (weekOffset > 0) "‹  " else "    ", 13.sp, weight = FontWeight.SemiBold, alpha = 0.7f)
            Caption(heading)
            if (focused && weekOffset < Calendar.WEEKS_AHEAD) LText("  ›", 13.sp, weight = FontWeight.SemiBold, alpha = 0.7f)
            Spacer(Modifier.weight(1f))
            Caption(month)
        }
        if (!Calendar.configured(LocalContext.current)) {
            LText(
                str(R.string.cal_not_connected), 13.sp, Modifier.padding(start = 6.dp, bottom = 8.dp),
                FontWeight.Medium, alpha = 0.6f,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll, enabled = false)
                    .heightIn(min = maxHeight)
                    .height(IntrinsicSize.Max),
            ) {
                days.forEachIndexed { i, day ->
                    // Feine Trennlinien statt Kästen.
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Color.White.copy(alpha = 0.10f)))
                    DayColumn(
                        day, day == today, day.isBefore(today), eventsOn(model.calendar, day, zone),
                        // Angewählt: alle Termine (zum Durchscrollen), sonst die ersten vier.
                        if (focused) Int.MAX_VALUE else MAX_PER_DAY, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun eventsOn(events: List<CalEvent>, day: LocalDate, zone: ZoneId): List<CalEvent> {
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    // Ganztägige zuerst, dann nach Beginn.
    return events.filter { it.start < end && (it.end > start || (it.end == it.start && it.start >= start)) }
        .sortedWith(compareBy({ !it.allDay }, { it.start }))
}

@Composable
private fun DayColumn(day: LocalDate, isToday: Boolean, isPast: Boolean, events: List<CalEvent>, max: Int, modifier: Modifier) {
    val dim = if (isPast) 0.45f else 1f
    Column(
        modifier
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val name = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, L10n.locale).removeSuffix(".")
        LText(name.uppercase(), 11.sp, weight = FontWeight.SemiBold, alpha = 0.7f * dim)
        Spacer(Modifier.height(4.dp))
        // Heute: Datum im roten Kreis wie bei Apple.
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (isToday) Color(0xFFFF3B30) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            LText(day.dayOfMonth.toString(), 16.sp, weight = FontWeight.SemiBold, alpha = dim)
        }
        Spacer(Modifier.height(6.dp))
        events.take(max).forEach { EventChip(it, day, dim) }
        if (events.size > max) {
            LText("+${events.size - max}", 11.sp, Modifier.padding(top = 2.dp), FontWeight.Medium, alpha = 0.6f * dim)
        }
    }
}

@Composable
private fun EventChip(e: CalEvent, day: LocalDate, dim: Float) {
    val color = Color(e.color)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.28f * dim)),
    ) {
        Box(Modifier.width(3.dp).height(34.dp).background(color.copy(alpha = dim)))
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            val zone = ZoneId.systemDefault()
            val startsToday = Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate() == day
            val time = when {
                e.allDay -> str(R.string.cal_all_day)
                startsToday -> TIME.format(Instant.ofEpochMilli(e.start).atZone(zone))
                else -> str(R.string.cal_continues)
            }
            LText(time, 10.sp, weight = FontWeight.Medium, alpha = 0.75f * dim)
            LText(e.title, 11.sp, weight = FontWeight.SemiBold, alpha = dim, align = TextAlign.Start)
        }
    }
}
