package de.tb.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.AppEntry
import de.tb.lumen.LauncherModel
import de.tb.lumen.PopupItem

/**
 * „Zuletzt geschaut“ über einer Favoriten-Kachel: die letzten Inhalte dieser App, auswählbar.
 * Hoch/Runter wandert durch die Liste, Runter aus der letzten Zeile (oder links/rechts) schließt
 * und gibt den Fokus an die Kachel zurück ([onLeave]).
 */
@Composable
fun HistoryPopup(
    app: AppEntry,
    entries: List<PopupItem>,
    model: LauncherModel,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit,
) {
    val rows = remember(entries.size) { List(entries.size) { FocusRequester() } }
    val focusedRow = remember { intArrayOf(-1) }
    Column(
        modifier
            .width(400.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val i = focusedRow[0]
                if (i < 0) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { if (i > 0) runCatching { rows[i - 1].requestFocus() }; true }
                    Key.DirectionDown -> { if (i < entries.lastIndex) runCatching { rows[i + 1].requestFocus() } else onLeave(); true }
                    Key.DirectionLeft, Key.DirectionRight -> { onLeave(); true }
                    else -> false
                }
            }
            // Liegt über Kalender & Co.: dunkler, fast deckender Grund unter dem Glas, sonst scheint es durch.
            .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
            .background(Color(0xE6201C26), RoundedCornerShape(28.dp))
            .glass(RoundedCornerShape(28.dp), LocalGlass.current)
            .padding(14.dp)
            .focusGroup(),
    ) {
        Caption("Weiterschauen · ${app.label}", Modifier.padding(start = 6.dp, top = 2.dp, bottom = 10.dp))
        entries.forEachIndexed { i, entry ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            val art = entry.art
            // Flache Zeilen wie in einer iOS-Liste: keine eigene Karte, nur der Fokus hinterlegt
            // die Zeile hell. Radien konzentrisch: Karte 28 = Abstand 14 + Zeile 14.
            var focused by remember { mutableStateOf(false) }
            val t by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "row")
            val rowShape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(rows[i])
                    .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.hasFocus) focusedRow[0] = i else if (focusedRow[0] == i) focusedRow[0] = -1
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { entry.open() }
                    .background(Color.White.copy(alpha = 0.16f * t), rowShape),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val appArt = model.art[app.packageName]
                        when {
                            art != null -> Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            appArt != null && appArt.isBanner ->
                                Image(appArt.forMode(model.dark), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            appArt != null -> Image(appArt.image, null, Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // Serie oben, Folge darunter.
                        LText(entry.head, 15.sp, weight = FontWeight.SemiBold)
                        entry.sub?.let { LText(it, 12.sp, Modifier.padding(top = 2.dp), FontWeight.Medium, alpha = 0.7f) }
                    }
                }
            }
        }
    }
}
