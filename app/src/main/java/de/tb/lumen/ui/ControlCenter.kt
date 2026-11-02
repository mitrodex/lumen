package de.tb.lumen.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.HomeWatcherService
import de.tb.lumen.LauncherModel
import kotlinx.coroutines.delay

private val ModuleShape = RoundedCornerShape(28.dp)

/** Kontrollzentrum im tvOS-Stil: Glas-Panel rechts mit Modulen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControlCenter(model: LauncherModel, onClose: () -> Unit, onOpenSettings: (Category) -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val wifi = remember { networkStatus(context) }
    val btAudio = remember { bluetoothAudioName(context) }
    val firstFocus = remember { FocusRequester() }
    var restartArmed by remember { mutableStateOf(false) }

    // Radien konzentrisch: Panel 44 = Abstand 16 + Modul 28.
    val panelShape = RoundedCornerShape(44.dp)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (model.dark) 0.55f else 0.40f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(24.dp)
                .width(400.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    shape = panelShape
                    clip = false
                    shadowElevation = 30.dp.toPx()
                }
                .frosted(panelShape)
                .glass(panelShape, glass)
                .padding(16.dp)
                // Fokus bleibt im Panel, solange es offen ist.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Kopf: Uhrzeit und Datum
            Column(Modifier.padding(start = 14.dp, top = 6.dp)) {
                LText(formatTime(model.now), 30.sp, weight = FontWeight.SemiBold, tracking = (-0.5).sp)
                LText(formatDate(model.now), 15.sp, weight = FontWeight.Medium, alpha = 0.7f)
            }

            // Module: runde Schalter in zwei Reihen (Verbindungen & Gerät)
            val toggleRow = Modifier.fillMaxWidth().glass(ModuleShape, glass).padding(vertical = 8.dp)
            Row(toggleRow, horizontalArrangement = Arrangement.SpaceEvenly) {
                CircleToggle(
                    LumenIcons.Wifi, "WLAN", wifi ?: "Aus", active = wifi != null, activeColor = SystemBlue,
                    modifier = Modifier.focusRequester(firstFocus),
                ) { onOpenSettings(Category.Wifi) }
                CircleToggle(
                    LumenIcons.Bluetooth, "Bluetooth", btAudio?.let { "Verbunden" } ?: "Koppeln",
                    active = btAudio != null, activeColor = SystemBlue,
                ) { onOpenSettings(Category.Bluetooth) }
                CircleToggle(
                    LumenIcons.Volume, "Töne", if (model.sounds) "Ein" else "Aus",
                    active = model.sounds, activeColor = Color.White,
                ) { model.toggleSounds() }
            }
            Row(toggleRow, horizontalArrangement = Arrangement.SpaceEvenly) {
                CircleToggle(
                    LumenIcons.Appearance, "Dunkel", if (model.dark) "Ein" else "Aus",
                    active = model.dark, activeColor = Color.White,
                ) { model.toggleDark() }
                CircleToggle(LumenIcons.Moon, "Ruhe", "Schlafen", active = false, activeColor = Color.White) {
                    onClose()
                    if (!HomeWatcherService.sleep()) {
                        Toast.makeText(context, "Ruhezustand braucht den Lumen-Dienst", Toast.LENGTH_SHORT).show()
                    }
                }
                // Zweistufig gegen versehentliches Auslösen: 1. Druck = scharf (rot), 2. Druck öffnet
                // „Mein Fire TV“ mit Fokus auf „Neu starten“ – dort genügt OK.
                CircleToggle(
                    LumenIcons.Restart, "Neustart", if (restartArmed) "Nochmal" else "Fire TV",
                    active = restartArmed, activeColor = SystemRed,
                ) {
                    if (!restartArmed) {
                        restartArmed = true
                    } else {
                        onClose()
                        val service = HomeWatcherService.instance
                        if (service != null) service.restartDevice()
                        else Toast.makeText(context, "Neustart braucht den Lumen-Dienst", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            LaunchedEffect(restartArmed) {
                if (restartArmed) {
                    delay(4_000)
                    restartArmed = false
                }
            }

            // Modul: Lautstärke
            VolumeModule()

            Spacer(Modifier.weight(1f))

            ModuleButton(LumenIcons.Settings, "Einstellungen", "Darstellung, WLAN, Bluetooth …") {
                onOpenSettings(Category.Appearance)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

/** Runder Schalter wie im iOS-Kontrollzentrum: aktiv = gefüllt. */
@Composable
private fun CircleToggle(
    icon: ImageVector,
    label: String,
    status: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "f")
    val a by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "a")
    val iconColor = if (active && activeColor == Color.White) InkColor else Color.White
    Column(modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer {
                    val s = 1f + 0.12f * f
                    scaleX = s
                    scaleY = s
                }
                .onFocusChanged { focused = it.isFocused }
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .glass(CircleShape, glass) { f }
                .drawBehind { if (a > 0f) drawCircle(activeColor.copy(alpha = a)) },
            contentAlignment = Alignment.Center,
        ) {
            Image(icon, null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(iconColor))
        }
        Spacer(Modifier.height(8.dp))
        LText(label, 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        LText(status, 11.sp, alpha = 0.65f, align = TextAlign.Center)
    }
}

/** Breiter Lautstärke-Regler: fokussieren, dann mit links/rechts regeln. */
@Composable
private fun VolumeModule() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    if (am == null || am.isVolumeFixed) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).glass(ModuleShape, glass).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(LumenIcons.Volume, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
            Spacer(Modifier.width(14.dp))
            LText("Lautstärke regelt der Fernseher", 15.sp, weight = FontWeight.Medium, alpha = 0.8f)
        }
        return
    }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var focused by remember { mutableStateOf(false) }
    val level by animateFloatAsState(volume / max.toFloat(), tween(150), label = "vol")
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "f")

    fun adjust(direction: Int) {
        // Flag 0 = keine Fire-OS-Lautstärkeanzeige.
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    Column {
        Caption("Lautstärke", Modifier.padding(start = 14.dp, bottom = 8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    val s = 1f + 0.03f * f
                    scaleX = s
                    scaleY = s
                }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    when (e.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (e.type == KeyEventType.KeyDown) {
                                adjust(if (e.key == Key.DirectionRight) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .glass(RoundedCornerShape(28.dp), glass) { f }
                .drawBehind {
                    val w = size.width * level
                    if (w > 0f) drawRoundRect(Color.White, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val onFill = level > 0.12f
            Image(
                LumenIcons.Volume, null,
                Modifier.padding(start = 18.dp).size(22.dp),
                colorFilter = ColorFilter.tint(if (onFill) InkColor.copy(alpha = 0.75f) else Color.White),
            )
        }
    }
}

@Composable
private fun ModuleButton(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassTile(onClick = onClick, modifier = Modifier.fillMaxWidth().height(70.dp), shape = ModuleShape, focusedScale = 1.04f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).glass(CircleShape, LocalGlass.current), contentAlignment = Alignment.Center) {
                Image(icon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                LText(title, 16.sp, weight = FontWeight.SemiBold)
                LText(subtitle, 13.sp, alpha = 0.65f)
            }
        }
    }
}

/** null = kein Netz. */
private fun networkStatus(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return null
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Verbunden"
    }
}

private fun bluetoothAudioName(context: Context): String? {
    val am = context.getSystemService(AudioManager::class.java) ?: return null
    return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?.productName?.toString()
}
