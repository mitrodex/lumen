package de.tb.lumen.ui

import de.tb.lumen.R
import de.tb.lumen.str

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.Screen
import de.tb.lumen.WeatherNow

/** Fokus-Ziele der Tabs – damit Zurück aus dem Inhalt hoch in die Leiste springen kann. */
class TabFocus {
    val home = FocusRequester()
    val apps = FocusRequester()
    val search = FocusRequester()

    /** Fokus auf den Tab des aktuellen Bildschirms (Einstellungen haben keinen → „Start“). */
    fun focus(screen: Screen) = runCatching {
        when (screen) {
            Screen.Apps -> apps
            Screen.Search -> search
            else -> home
        }.requestFocus()
    }
}

/** tvOS-artige Glas-Tab-Leiste oben in der Mitte, rechts der Knopf fürs Kontrollzentrum. */
@Composable
fun TopBar(
    screen: Screen,
    timeText: String?,
    tabs: TabFocus,
    onSelect: (Screen) -> Unit,
    onControl: () -> Unit,
    onFocusInBar: (Boolean) -> Unit,
    weather: WeatherNow? = null,
    onWeatherFocus: (Boolean) -> Unit = {},
) {
    Box(Modifier.fillMaxWidth().height(52.dp).onFocusChanged { onFocusInBar(it.hasFocus) }) {
        if (timeText != null) {
            LText(timeText, 20.sp, Modifier.align(Alignment.CenterStart), FontWeight.SemiBold)
        } else if (weather != null) {
            WeatherPill(weather, Modifier.align(Alignment.CenterStart), onWeatherFocus)
        }
        Row(
            Modifier
                .align(Alignment.Center)
                .glass(RoundedCornerShape(26.dp), LocalGlass.current)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab(str(R.string.tab_home), screen == Screen.Home, Modifier.focusRequester(tabs.home)) { onSelect(Screen.Home) }
            Tab(str(R.string.tab_apps), screen == Screen.Apps, Modifier.focusRequester(tabs.apps)) { onSelect(Screen.Apps) }
            Tab(str(R.string.tab_search), screen == Screen.Search, Modifier.focusRequester(tabs.search)) { onSelect(Screen.Search) }
        }
        RoundButton(LumenIcons.Toggles, onControl, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "tab")
    val s by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "sel")
    Box(
        modifier
            .height(42.dp)
            .graphicsLayer {
                val k = 1f + 0.08f * f
                scaleX = k
                scaleY = k
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .drawBehind {
                // Ausgewählt: weiße Pille. Fokussiert (nicht ausgewählt): helle Glas-Pille.
                val r = CornerRadius(size.height / 2)
                val a = maxOf(s, 0.34f * f)
                if (a > 0f) drawRoundRect(Color.White.copy(alpha = a), cornerRadius = r)
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        LText(
            label, 16.sp,
            weight = FontWeight.SemiBold,
            color = if (selected) InkColor else Color.White,
        )
    }
}

@Composable
fun RoundButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassTile(onClick = onClick, modifier = modifier.size(48.dp), shape = CircleShape, focusedScale = 1.14f) {
        Image(icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
    }
}
