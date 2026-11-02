package de.tb.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.AirPlayMusic
import de.tb.lumen.MusicNow
import kotlinx.coroutines.delay

/** Position zweimal pro Sekunde hochrechnen (die App meldet sie nur bei Änderungen). */
@Composable
private fun livePosition(m: MusicNow): Long {
    var pos by remember { mutableLongStateOf(m.positionNow()) }
    LaunchedEffect(m) {
        while (true) {
            pos = m.positionNow()
            delay(500)
        }
    }
    return pos
}

/** „Jetzt läuft“ wie im iOS-Kontrollzentrum: Cover, Titel, Fortschritt, Zurück/Play/Weiter. */
@Composable
fun MusicWidget(
    m: MusicNow,
    music: AirPlayMusic,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    /** Play/Pause – Einstieg von unten (Pfeil hoch aus den Favoriten). */
    playFocus: FocusRequester = remember { FocusRequester() },
) {
    val pos = livePosition(m)
    val shape = RoundedCornerShape(24.dp)
    val coverFocus = remember { FocusRequester() }
    var onCover by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(320.dp)
            // Hoch/Runter regelt der Player selbst: Tasten ↔ Cover, Cover → Tabs, Tasten → Favoriten.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { if (onCover) onUp() else runCatching { coverFocus.requestFocus() }; true }
                    Key.DirectionDown -> { if (onCover) runCatching { playFocus.requestFocus() } else onDown(); true }
                    else -> false
                }
            }
            .glass(shape, LocalGlass.current)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Cover anwählbar → große Ansicht. Radien konzentrisch: Karte 24 = Abstand 12 + Cover 12.
            Box(Modifier.focusRequester(coverFocus).onFocusChanged { onCover = it.hasFocus }) {
                Cover(m, 48.dp, RoundedCornerShape(12.dp), onExpand)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                LText(m.title, 16.sp, weight = FontWeight.SemiBold)
                LText(m.artist ?: "AirPlay", 13.sp, weight = FontWeight.Medium, alpha = 0.7f)
            }
        }
        Spacer(Modifier.height(10.dp))
        Progress(m, pos)
        Controls(m, music, small = 36.dp, big = 44.dp, gap = 18.dp, playModifier = Modifier.focusRequester(playFocus))
    }
}

/**
 * Große Player-Ansicht über der Startseite: Hintergrund abgedunkelt und mit dem weich
 * hochskalierten Cover eingefärbt (wirkt wie Unschärfe), Player groß und zentriert.
 * Zurück schließt (macht MainActivity).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExpandedPlayer(m: MusicNow, music: AirPlayMusic, backdrop: androidx.compose.ui.graphics.ImageBitmap?) {
    val pos = livePosition(m)
    val playFocus = remember { FocusRequester() }
    val dark = LocalDarkMode.current
    // Hinter dem Player: die weichgezeichnete Startseite mit weißlichem Glas-Schleier (wie bei Apple).
    Box(Modifier.fillMaxSize().background(Color(0xFF20222A))) {
        backdrop?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        }
        Box(Modifier.fillMaxSize().background(if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.30f else 0.12f)))
        Column(
            Modifier
                .align(Alignment.Center)
                .width(440.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cover(m, 220.dp, RoundedCornerShape(24.dp), onClick = null)
            Spacer(Modifier.height(20.dp))
            LText(m.title, 28.sp, Modifier.fillMaxWidth(), FontWeight.Bold, align = TextAlign.Center)
            LText(m.artist ?: "AirPlay", 18.sp, Modifier.fillMaxWidth().padding(top = 4.dp), FontWeight.Medium, alpha = 0.75f, align = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Progress(m, pos)
            Spacer(Modifier.height(6.dp))
            Controls(m, music, small = 52.dp, big = 64.dp, gap = 34.dp, playModifier = Modifier.focusRequester(playFocus))
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun Cover(m: MusicNow, size: Dp, shape: RoundedCornerShape, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        if (m.art != null) {
            Image(m.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(LumenIcons.Volume, null, Modifier.size(size * 0.45f), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.7f)))
        }
    }
    if (onClick != null) {
        GlassTile(onClick = onClick, modifier = Modifier.size(size), shape = shape, focusedScale = 1.12f, shadow = true) { content() }
    } else {
        Box(Modifier.size(size).glass(shape, LocalGlass.current).clip(shape), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Progress(m: MusicNow, pos: Long) {
    val frac = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f
    Box(
        Modifier.fillMaxWidth().height(4.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
            drawRoundRect(Color.White, size = Size(size.width * frac, size.height), cornerRadius = r)
        },
    )
    Spacer(Modifier.height(3.dp))
    Row(Modifier.fillMaxWidth()) {
        LText(clock(pos), 11.sp, alpha = 0.6f)
        Spacer(Modifier.weight(1f))
        if (m.durationMs > 0) LText("-" + clock(m.durationMs - pos), 11.sp, alpha = 0.6f)
    }
}

@Composable
private fun Controls(m: MusicNow, music: AirPlayMusic, small: Dp, big: Dp, gap: Dp, playModifier: Modifier = Modifier) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(LumenIcons.Previous, small) { music.previous() }
        ControlButton(if (m.playing) LumenIcons.Pause else LumenIcons.Play, big, playModifier) { music.playPause() }
        ControlButton(LumenIcons.Next, small) { music.next() }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassTile(onClick = onClick, modifier = modifier.size(size), shape = CircleShape, focusedScale = 1.15f) {
        Image(icon, null, Modifier.size(size * 0.5f), colorFilter = ColorFilter.tint(Color.White))
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
