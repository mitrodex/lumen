package de.tb.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.HourForecast
import de.tb.lumen.Weather
import de.tb.lumen.WeatherNow

/** Wetter als Glas-Pille oben links in der Kopfleiste; fokussiert klappt die Vorschau auf. */
@Composable
fun WeatherPill(w: WeatherNow, modifier: Modifier, onFocus: (Boolean) -> Unit) {
    GlassTile(
        onClick = {},
        modifier = modifier.height(48.dp).onFocusChanged { onFocus(it.isFocused) },
        shape = RoundedCornerShape(24.dp),
        focusedScale = 1.06f,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                LumenIcons.weather(w.code, w.isDay), null, Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.95f)),
            )
            Spacer(Modifier.width(10.dp))
            LText("${w.tempC}°", 18.sp, weight = FontWeight.SemiBold)
            LText("  ${Weather.describe(w.code, w.isDay)}", 15.sp, weight = FontWeight.Medium, alpha = 0.7f)
        }
    }
}

/** Glas-Karte mit Ort und den nächsten Stunden. */
@Composable
fun ForecastCard(w: WeatherNow, hours: List<HourForecast>) {
    val shape = RoundedCornerShape(24.dp)
    // Milchglas wie das Kontrollzentrum: die Karte liegt über der Uhr und muss sie verdecken.
    Column(
        Modifier
            .graphicsLayer {
                this.shape = shape
                clip = false
                shadowElevation = 20.dp.toPx()
            }
            .frosted(shape)
            .glass(shape, LocalGlass.current)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Caption("${w.place} · nächste Stunden", Modifier.padding(start = 2.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hours.forEach { h ->
                Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LText("%02d".format(h.hour), 13.sp, weight = FontWeight.Medium, alpha = 0.7f)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        LumenIcons.weather(h.code, h.isDay), null, Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    LText("${h.tempC}°", 16.sp, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}
