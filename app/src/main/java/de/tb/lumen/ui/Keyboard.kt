package de.tb.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Eigene Bildschirmtastatur im Glas-Look (QWERTZ mit Umlauten), für die Fernbedienung gemacht:
 * große Tasten, Fokus bleibt in der Tastatur, Zurück schließt (über [InterceptBack]).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassKeyboard(onChar: (Char) -> Unit, onDelete: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var numbers by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val rows = if (numbers) {
        listOf("1234567890", "-/:;()&@\".", ",?!'#+*=_%")
    } else {
        listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß")
    }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .glass(shape, LocalGlass.current)
            .padding(14.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { i, ch ->
                    // Startfokus mitten in der zweiten Reihe („g“) – kürzeste Wege überallhin.
                    val m = if (r == 1 && i == 4) Modifier.focusRequester(first) else Modifier
                    Key(ch.toString(), m) { onChar(ch) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(if (numbers) "ABC" else "123", width = 84.dp, small = true) { numbers = !numbers }
            Key("Leerzeichen", width = 260.dp, small = true) { onChar(' ') }
            IconKey(LumenIcons.Backspace, 84.dp, onDelete)
            Key("Fertig", width = 110.dp, small = true, accent = true, onClick = onDone)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    small: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    GlassTile(onClick = onClick, modifier = modifier.width(width).height(46.dp), shape = RoundedCornerShape(12.dp), focusedScale = 1.14f) { focused ->
        LText(
            label, if (small) 15.sp else 20.sp,
            weight = if (accent) FontWeight.Bold else FontWeight.Medium,
            color = if (accent && !focused) SystemBlue.copy(alpha = 1f).let { Color(0xFF64A8FF) } else Color.White,
        )
    }
}

@Composable
private fun IconKey(icon: androidx.compose.ui.graphics.vector.ImageVector, width: Dp, onClick: () -> Unit) {
    GlassTile(onClick = onClick, modifier = Modifier.width(width).height(46.dp), shape = RoundedCornerShape(12.dp), focusedScale = 1.14f) {
        Image(icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
    }
}
