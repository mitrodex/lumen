package de.tb.lumen.ui

import de.tb.lumen.L10n
import de.tb.lumen.R
import de.tb.lumen.str

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tb.lumen.AppRepository
import de.tb.lumen.Appearance
import de.tb.lumen.BluetoothController
import de.tb.lumen.LauncherModel
import de.tb.lumen.SystemSettings
import de.tb.lumen.Weather
import de.tb.lumen.WifiController
import de.tb.lumen.WifiNetwork
import kotlinx.coroutines.launch

/** Seiten der Lumen-Einstellungen; das Kontrollzentrum springt direkt zu WLAN bzw. Bluetooth. */
enum class Category(@androidx.annotation.StringRes private val titleRes: Int, val icon: () -> ImageVector) {
    Appearance(R.string.cat_appearance, { LumenIcons.Appearance }),
    Screensaver(R.string.cat_screensaver, { LumenIcons.Moon }),
    Wifi(R.string.cc_wifi, { LumenIcons.Wifi }),
    Bluetooth(R.string.cc_bluetooth, { LumenIcons.Bluetooth }),
    WeatherCat(R.string.cat_weather, { LumenIcons.Sun }),
    System(R.string.cat_system, { LumenIcons.Settings });

    val title: String get() = str(titleRes)
}

private val PanelShape = RoundedCornerShape(32.dp)
// Konzentrisch: Panel 32 = Abstand 16 + Zeile 16.
private val RowShape = RoundedCornerShape(16.dp)
private val SystemGreen = Color(0xFF30D158)

/** Eigene Einstellungen im tvOS-Stil: links Kategorien (Auswahl per Fokus), rechts Zeilen. */
@Composable
fun SettingsScreen(model: LauncherModel, start: Category = Category.Appearance) {
    var category by remember(start) { mutableStateOf(start) }
    val categoryFocus = remember { Category.entries.associateWith { FocusRequester() } }
    val context = LocalContext.current
    val canWrite = remember { SystemSettings.canWrite(context) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            LText(str(R.string.cc_settings), 34.sp, Modifier.padding(start = 8.dp, bottom = 18.dp), FontWeight.SemiBold, shadow = true)
            Category.entries.forEach { c ->
                CategoryItem(
                    c, selected = c == category,
                    modifier = Modifier.focusRequester(categoryFocus.getValue(c)),
                ) { category = c }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.width(28.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = 4.dp)
                .glass(PanelShape, LocalGlass.current),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!canWrite) {
                    item { Notice(str(R.string.set_missing_permission)) }
                }
                when (category) {
                    Category.Appearance -> appearanceRows(model)
                    Category.Screensaver -> screensaverRows()
                    Category.Wifi -> item { WifiPane() }
                    Category.Bluetooth -> item { BluetoothPane() }
                    Category.WeatherCat -> item { WeatherPane(model) }
                    Category.System -> systemRows()
                }
            }
        }
    }

    LaunchedEffect(start) { runCatching { categoryFocus.getValue(start).requestFocus() } }
}

@Composable
private fun CategoryItem(c: Category, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "f")
    val s by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "s")
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                val k = 1f + 0.04f * f
                scaleX = k
                scaleY = k
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) { onFocus() }
            .drawBehind {
                // Fokus: weiße Pille (wie tvOS), nur ausgewählt: zarte Glas-Pille.
                val r = CornerRadius(size.height / 2)
                if (f > 0f) drawRoundRect(Color.White.copy(alpha = 0.92f * f), cornerRadius = r)
                if (s > 0f && f < 1f) drawRoundRect(Color.White.copy(alpha = 0.14f * s * (1f - f)), cornerRadius = r)
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (focused) InkColor else Color.White
        Image(c.icon(), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(ink))
        Spacer(Modifier.width(14.dp))
        LText(c.title, 17.sp, weight = FontWeight.SemiBold, color = ink)
    }
}

// --- Bausteine ----------------------------------------------------------------------------

@Composable
private fun SettingRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    toggle: Boolean? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit = {},
) {
    GlassTile(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (subtitle != null) 70.dp else 58.dp),
        shape = RowShape,
        focusedScale = 1.02f,
        onKey = onKey,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LText(title, 17.sp, weight = FontWeight.Medium)
                if (subtitle != null) LText(subtitle, 13.sp, alpha = 0.6f)
            }
            if (value != null) LText(value, 16.sp, alpha = 0.65f)
            if (toggle != null) Switch(toggle)
        }
    }
}

/** iOS-Schalter. */
@Composable
private fun Switch(on: Boolean) {
    val t by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "sw")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.22f * (1f - t)), cornerRadius = r)
                drawRoundRect(SystemGreen.copy(alpha = t), cornerRadius = r)
                val knob = size.height / 2 - 2.dp.toPx()
                val x = size.height / 2 + (size.width - size.height) * t
                drawCircle(Color.White, knob, Offset(x, size.height / 2))
            },
    )
}

@Composable
private fun Notice(text: String) {
    Box(
        Modifier.fillMaxWidth().background(Color(0x33FF9F0A), RowShape).padding(horizontal = 20.dp, vertical = 14.dp),
    ) { LText(text, 13.sp, alpha = 0.9f) }
}

private fun LazyListScope.header(text: String) = item {
    Caption(text, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
}

/** Links/Rechts ändert einen Wert (z. B. Uhrzeit). */
private fun stepKeys(onStep: (Int) -> Unit): (KeyEvent) -> Boolean = { e ->
    when (e.key) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (e.type == KeyEventType.KeyDown) onStep(if (e.key == Key.DirectionRight) 1 else -1)
            true
        }
        else -> false
    }
}

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1).mod(size)]

// --- Darstellung --------------------------------------------------------------------------

private fun LazyListScope.appearanceRows(model: LauncherModel) {
    header(str(R.string.set_dark_mode))
    item {
        val ctx = LocalContext.current
        val p = remember { Appearance.prefs(ctx) }
        var v by remember { mutableIntStateOf(0) }
        @Suppress("UNUSED_EXPRESSION") v
        val mode = Appearance.mode(p)
        val label = mapOf(Appearance.Mode.Auto to str(R.string.set_auto), Appearance.Mode.Always to str(R.string.set_always), Appearance.Mode.Never to str(R.string.set_never))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow(str(R.string.set_dark_mode), label[mode]) {
                Appearance.setMode(p, Appearance.Mode.entries.toList().after(mode)); v++; model.tick()
            }
            if (mode == Appearance.Mode.Auto) {
                val from = Appearance.darkFrom(p)
                val until = Appearance.darkUntil(p)
                SettingRow(str(R.string.set_dark_from), "‹ %02d:00 ›".format(from), onKey = stepKeys { Appearance.setHours(p, from + it, until); v++; model.tick() }) {
                    Appearance.setHours(p, from + 1, until); v++; model.tick()
                }
                SettingRow(str(R.string.set_light_from), "‹ %02d:00 ›".format(until), onKey = stepKeys { Appearance.setHours(p, from, until + it); v++; model.tick() }) {
                    Appearance.setHours(p, from, until + 1); v++; model.tick()
                }
            }
        }
    }
    header(str(R.string.set_motion))
    item {
        SettingRow(
            str(R.string.set_animated_bg),
            toggle = model.animatedBackground,
            subtitle = str(R.string.set_animated_bg_sub),
        ) { model.toggleAnimatedBackground() }
    }
    item {
        val ctx = LocalContext.current
        var scale by remember { mutableFloatStateOf(SystemSettings.animationScale(ctx)) }
        val options = listOf(1f to str(R.string.set_anim_normal), 0.5f to str(R.string.set_anim_fast), 0f to str(R.string.cc_off))
        val current = options.minBy { kotlin.math.abs(it.first - scale) }
        SettingRow(str(R.string.set_animations), current.second, subtitle = str(R.string.set_animations_sub)) {
            val next = options.after(current)
            if (SystemSettings.setAnimationScale(ctx, next.first)) scale = next.first
        }
    }
}

// --- Bildschirmschoner --------------------------------------------------------------------

private fun LazyListScope.screensaverRows() {
    item {
        val ctx = LocalContext.current
        var lumen by remember { mutableStateOf(SystemSettings.isLumenScreensaver(ctx)) }
        var delay by remember { mutableIntStateOf(SystemSettings.screensaverDelayMin(ctx)) }
        var sleep by remember { mutableIntStateOf(SystemSettings.sleepMin(ctx)) }
        val delays = listOf(5, 10, 15, 30)
        val sleeps = listOf(20, 60, 120, 240, -1)
        fun sleepLabel(m: Int) = when {
            m < 0 -> str(R.string.set_never)
            m < 60 -> str(R.string.set_minutes, m)
            else -> str(R.string.set_hours, m / 60)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow(str(R.string.set_lumen_screensaver), toggle = lumen, subtitle = if (lumen) str(R.string.set_ss_lumen_sub) else str(R.string.set_ss_amazon_sub)) {
                if (SystemSettings.setLumenScreensaver(ctx, !lumen)) lumen = !lumen
            }
            SettingRow(str(R.string.set_ss_start), str(R.string.set_minutes, delay)) {
                val next = delays.firstOrNull { it > delay } ?: delays.first()
                if (SystemSettings.setScreensaverDelayMin(ctx, next)) delay = next
            }
            SettingRow(str(R.string.set_sleep_after), sleepLabel(sleep), subtitle = str(R.string.set_sleep_sub)) {
                val next = sleeps.getOrNull(sleeps.indexOf(sleep) + 1) ?: sleeps.first()
                if (SystemSettings.setSleepMin(ctx, next)) sleep = next
            }
        }
    }
}

// --- WLAN ---------------------------------------------------------------------------------

@Composable
private fun WifiPane() {
    val ctx = LocalContext.current
    val wifi = remember { WifiController(ctx) }
    DisposableEffect(Unit) {
        wifi.start()
        onDispose { wifi.stop() }
    }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(str(R.string.set_scan_networks), wifi.status ?: str(R.string.set_found, wifi.networks.size)) { wifi.scan() }
        Caption(str(R.string.set_networks), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
        wifi.networks.forEach { net ->
            val value = when {
                net.connected -> str(R.string.set_connected_check)
                net.saved -> str(R.string.set_saved)
                net.secured -> str(R.string.set_secured)
                else -> str(R.string.set_open)
            }
            SettingRow(net.ssid, value, subtitle = str(R.string.set_signal, "●".repeat(net.level + 1) + "○".repeat(3 - net.level))) {
                when {
                    net.connected -> Unit
                    net.saved || !net.secured -> wifi.connect(net, null)
                    else -> expanded = if (expanded == net.ssid) null else net.ssid
                }
            }
            if (expanded == net.ssid) PasswordRow(net) { pw -> if (wifi.connect(net, pw)) expanded = null }
        }
    }
}

@Composable
private fun PasswordRow(net: WifiNetwork, onSubmit: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    TextRow(str(R.string.set_password_for, net.ssid), pw, { pw = it }, password = true, modifier = Modifier.focusRequester(focus)) { onSubmit(pw) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Eingabezeile; OK auf der Bildschirmtastatur bestätigt. */
@Composable
private fun TextRow(
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val focused = remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(58.dp).glass(RowShape, LocalGlass.current) { focused.floatValue },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .onFocusChanged { focused.floatValue = if (it.isFocused) 1f else 0f },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) LText(hint, 17.sp, alpha = 0.5f)
                    inner()
                }
            },
        )
    }
}

// --- Bluetooth ----------------------------------------------------------------------------

@Composable
private fun BluetoothPane() {
    val ctx = LocalContext.current
    val bt = remember { BluetoothController(ctx) }
    DisposableEffect(Unit) {
        bt.start()
        onDispose { bt.stop() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            str(R.string.set_bt_search),
            if (bt.scanning) str(R.string.set_bt_searching) else null,
            subtitle = bt.status ?: str(R.string.set_bt_hint),
        ) { bt.scan() }
        Caption(str(R.string.set_audio_devices), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
        if (bt.devices.isEmpty()) {
            LText(str(R.string.set_no_audio), 15.sp, Modifier.padding(start = 12.dp, top = 6.dp), alpha = 0.6f)
        }
        bt.devices.forEach { d ->
            val value = when {
                d.connected -> str(R.string.set_connected_check)
                d.bonded -> str(R.string.set_connect)
                else -> str(R.string.cc_pair)
            }
            SettingRow(d.name, value, subtitle = if (d.connected) str(R.string.set_press_ok_disconnect) else null) { bt.activate(d) }
        }
    }
}

// --- Wetter -------------------------------------------------------------------------------

@Composable
private fun WeatherPane(model: LauncherModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val w = model.weather
        SettingRow(
            str(R.string.set_current_place),
            w?.place ?: str(R.string.set_not_set),
            subtitle = w?.let { "${it.tempC}° · ${Weather.describe(it.code, it.isDay)}" },
        )
        Caption(str(R.string.set_change_place), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
        TextRow(str(R.string.set_enter_city), city, { city = it }) {
            if (city.isBlank()) return@TextRow
            msg = str(R.string.set_searching_city, city)
            scope.launch {
                model.setWeatherPlace(city)
                msg = if (Weather.hasPlace(ctx) && model.weather?.place?.isNotBlank() == true) str(R.string.set_saved_place, model.weather?.place.orEmpty()) else str(R.string.set_place_not_found)
            }
        }
        msg?.let { LText(it, 14.sp, Modifier.padding(start = 12.dp, top = 4.dp), alpha = 0.7f) }
    }
}

// --- System -------------------------------------------------------------------------------

/** Sprache der Oberfläche: System → English → Deutsch; baut den Bildschirm danach neu auf. */
@Composable
private fun LanguageRow() {
    val ctx = LocalContext.current
    val choice = L10n.choice(ctx)
    val label = when (choice) {
        L10n.Choice.System -> str(R.string.lang_system)
        L10n.Choice.English -> str(R.string.lang_en)
        L10n.Choice.German -> str(R.string.lang_de)
    }
    SettingRow(str(R.string.set_language), label) {
        L10n.setChoice(ctx, L10n.Choice.entries.toList().after(choice))
        (ctx as? android.app.Activity)?.recreate()
    }
}

private fun LazyListScope.systemRows() {
    item {
        val ctx = LocalContext.current
        val canWrite = SystemSettings.canWrite(ctx)
        var adb by remember { mutableStateOf(SystemSettings.adbEnabled(ctx)) }
        var adbArmed by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow(
                str(R.string.set_self_heal),
                if (canWrite) str(R.string.set_active) else str(R.string.cc_off),
                subtitle = str(R.string.set_self_heal_sub),
            )
            SettingRow(
                str(R.string.set_adb),
                toggle = adb,
                subtitle = when {
                    adbArmed -> str(R.string.set_adb_armed)
                    else -> str(R.string.set_adb_sub)
                },
            ) {
                if (adb && !adbArmed) {
                    adbArmed = true
                } else {
                    if (SystemSettings.setAdbEnabled(ctx, !adb)) adb = !adb
                    adbArmed = false
                }
            }
            SettingRow(str(R.string.set_all_fire), "›", subtitle = str(R.string.set_all_fire_sub)) {
                AppRepository.openFireSettings(ctx)
            }
            val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            LanguageRow()
            SettingRow(str(R.string.app_name), str(R.string.set_version, version.orEmpty()))
        }
    }
}
