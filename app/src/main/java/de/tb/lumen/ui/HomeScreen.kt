package de.tb.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
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
import de.tb.lumen.Weather
import de.tb.lumen.WeatherNow
import de.tb.lumen.HourForecast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.withContext
import de.tb.lumen.AppRepository
import de.tb.lumen.AppArt
import de.tb.lumen.NowPlaying
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

internal fun formatTime(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
internal fun formatDate(ms: Long): String = dateFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** Startseite: große Uhr wie auf dem Sperrbildschirm, „Weiterschauen“-Widget, Favoriten im Glas-Dock. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    model: LauncherModel,
    focusKey: Int,
    hideClock: Boolean = false,
    onExpandMusic: () -> Unit = {},
    onUpToTabs: () -> Unit,
) {
    var continueHeight by remember { mutableIntStateOf(0) }
    var musicFocusedOuter by remember { mutableStateOf(false) }
    // Wetter-Vorschau offen: Uhr weich ausblenden, damit die Karte frei steht.
    val clockAlpha by animateFloatAsState(if (hideClock) 0f else 1f, tween(280), label = "clock")
    // Rechts oben: „Weiterschauen“ (Inhalts-Vorschau, derzeit aus) oder der Wochenkalender.
    // Der Kalender ist anwählbar, um durch Wochen und Termine zu blättern.
    val continueApp = if (NowPlaying.ENABLED) model.continueApp() else null
    val hasSide = continueApp != null
    val favorites = model.favoriteApps()
    val firstFocus = remember { FocusRequester() }
    val musicPlayFocus = remember { FocusRequester() }
    val calendarFocus = remember { FocusRequester() }
    var calendarFocused by remember { mutableStateOf(false) }
    val introShimmer = remember { Animatable(0f) }
    // Einstieg in die Favoriten: zuletzt gewählte Kachel, sonst die erste – nicht die,
    // die geometrisch zufällig am nächsten liegt. Nur gerade gezeichnete Kacheln sind Ziele.
    val tileFocus = remember { mutableMapOf<String, FocusRequester>() }
    val composedTiles = remember { mutableSetOf<String>() }

    fun focusDock(): Boolean {
        val target = model.lastDockPackage?.takeIf { it in composedTiles }
            ?: favorites.firstOrNull()?.packageName?.takeIf { it in composedTiles }
            ?: return false
        return runCatching { tileFocus[target]?.requestFocus() }.isSuccess
    }

    // „Zuletzt geschaut“-Popup über einer Favoriten-Kachel: nur per langem OK (Fokus direkt im Popup).
    var hoverPkg by remember { mutableStateOf<String?>(null) }
    var dockFocused by remember { mutableStateOf(false) }
    var popupFocused by remember { mutableStateOf(false) }
    var popupPkg by remember { mutableStateOf<String?>(null) }
    var popupWantsFocus by remember { mutableStateOf(false) }
    // Nach dem Verlassen nicht sofort wieder aufgehen, erst nach einem Kachelwechsel.
    var dismissedPkg by remember { mutableStateOf<String?>(null) }
    val popupFirst = remember { FocusRequester() }
    val longPress = remember { booleanArrayOf(false) }
    val tileCenters = remember { mutableStateMapOf<String, Float>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootWidth by remember { mutableIntStateOf(0) }
    var dockTop by remember { mutableFloatStateOf(0f) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }

    fun closePopup(refocus: Boolean) {
        val p = popupPkg ?: return
        popupPkg = null
        dismissedPkg = p
        if (refocus) runCatching { tileFocus[p]?.requestFocus() }
    }

    LaunchedEffect(hoverPkg, dockFocused, popupFocused) {
        if (popupFocused) return@LaunchedEffect
        if (!dockFocused) { popupPkg = null; return@LaunchedEffect }
        // Öffnet nur per langem OK; auf eine andere Kachel weiter → zu.
        if (popupPkg != hoverPkg) popupPkg = null
    }
    LaunchedEffect(popupPkg, popupWantsFocus) {
        if (!popupWantsFocus || popupPkg == null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { popupFirst.requestFocus() }
        popupWantsFocus = false
    }
    InterceptBack(enabled = popupPkg != null) { closePopup(refocus = true) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootOrigin = it.positionInRoot()
                rootWidth = it.size.width
            }
            // Nach dem langen OK springt der Fokus ins Popup – die restlichen Wiederholungen und das
            // Loslassen dürfen dort keinen Eintrag (und damit die App) öffnen.
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!ok || !longPress[0]) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) longPress[0] = false
                true
            },
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // Pfeil runter aus dem oberen Bereich → gezielt in die Favoriten;
                // Pfeil hoch → in die Tab-Leiste (nicht auf den näher liegenden Kontrollzentrum-Knopf).
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        // Im Musik-Player und im Kalender regeln die Hoch/Runter selbst.
                        Key.DirectionUp, Key.DirectionDown -> if (musicFocusedOuter || calendarFocused) false else null
                        else -> null
                    } ?: when (e.key) {
                        Key.DirectionDown -> favorites.isNotEmpty() && focusDock()
                        Key.DirectionUp -> {
                            onUpToTabs()
                            true
                        }
                        else -> false
                    }
                },
            verticalAlignment = Alignment.Top,
        ) {
            // Versatz gleicht den Schrift-Innenabstand aus: Oberkante Begrüßung = Widget-Oberkante.
            // Läuft AirPlay-Musik, schrumpft die Uhr animiert und macht Platz für „Jetzt läuft“.
            val music = model.music.now
            var musicFocused by remember { mutableStateOf(false) }
            val m by animateFloatAsState(if (music != null) 1f else 0f, tween(450), label = "music")
            // Linke Spalte genau so hoch wie die Weiterschauen-Kachel → Player-Unterkante bündig.
            val density = LocalDensity.current
            val colHeight = if (continueHeight > 0) Modifier.height(with(density) { continueHeight.toDp() }) else Modifier
            Column(Modifier.weight(1f).then(colHeight)) {
                Column(Modifier.offset(y = (-4).dp).graphicsLayer { alpha = clockAlpha }) {
                    // Bei Musik macht die Begrüßung Platz (blendet aus und klappt zusammen).
                    LText(
                        greeting(model.now), 22.sp,
                        Modifier.padding(start = 6.dp).height((30 * (1 - m)).dp).graphicsLayer { alpha = 1 - m },
                        FontWeight.Medium, alpha = 0.85f, shadow = true,
                    )
                    LText(
                        formatTime(model.now), (104 - 40 * m).sp, Modifier.offset(y = (-10 + 4 * m).dp),
                        weight = FontWeight.Thin, tracking = (-2 + m).sp, shadow = true,
                    )
                    LText(
                        formatDate(model.now), 22.sp, Modifier.padding(start = 6.dp).offset(y = (-18 + 10 * m).dp),
                        FontWeight.Medium, alpha = 0.85f, shadow = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                androidx.compose.animation.AnimatedVisibility(
                    visible = music != null,
                    enter = fadeIn(tween(350, delayMillis = 150)) + slideInVertically(tween(450)) { it / 3 },
                    exit = fadeOut(tween(200)),
                ) {
                    // Letzten Stand festhalten, damit das Ausblenden nicht leer ist.
                    val last = remember { mutableStateOf(music) }
                    if (music != null) last.value = music
                    last.value?.let {
                        MusicWidget(
                            it, model.music,
                            Modifier.onFocusChanged { f ->
                                musicFocused = f.hasFocus
                                musicFocusedOuter = f.hasFocus
                            },
                            onExpand = onExpandMusic,
                            onUp = onUpToTabs,
                            onDown = { if (favorites.isNotEmpty()) focusDock() },
                            playFocus = musicPlayFocus,
                        )
                    }
                }
                // Endet die Musik, während man im Widget ist: Fokus auf „Start“, nicht ins Leere/aufs Wetter.
                LaunchedEffect(music == null) {
                    if (music == null && musicFocused) {
                        musicFocused = false
                        onUpToTabs()
                    }
                }
            }
            if (continueApp != null) {
                ContinueWidget(
                    continueApp, model,
                    Modifier.width(300.dp).focusRequester(firstFocus).onSizeChanged { continueHeight = it.height },
                )
            } else {
                WeekCalendar(
                    model,
                    // Läuft Musik, rückt der Kalender etwas zusammen – Platz für den Player links.
                    Modifier.width((560 - 64 * m).dp).fillMaxHeight().onSizeChanged { continueHeight = it.height },
                    focus = calendarFocus,
                    onFocused = { calendarFocused = it },
                    onUp = onUpToTabs,
                    onDown = { if (favorites.isNotEmpty()) focusDock() },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Glas-Dock wie bei iPadOS: ein gemeinsamer Glas-Träger für die Favoriten.
        // Radien konzentrisch: Dock 32 = Abstand 16 + Kachel 16.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                // Pfeil hoch aus den Favoriten gezielt aufs Widget (bzw. die Tabs) – nie aufs Wetter.
                .onPreviewKeyEvent { e ->
                    if (e.key != Key.DirectionUp || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Popup offen: hinein. Läuft Musik: auf Play/Pause, sonst aufs Widget bzw. in die Tabs.
                    when {
                        popupPkg != null -> runCatching { popupFirst.requestFocus() }
                        model.music.now != null -> runCatching { musicPlayFocus.requestFocus() }
                        hasSide -> runCatching { firstFocus.requestFocus() }
                        else -> runCatching { calendarFocus.requestFocus() }.onFailure { onUpToTabs() }
                    }
                    true
                }
                .onFocusChanged { dockFocused = it.hasFocus }
                .onGloballyPositioned { dockTop = it.positionInRoot().y }
                .glass(RoundedCornerShape(32.dp), LocalGlass.current),
        ) {
            LazyRow(
                Modifier
                    .fadeEdges(20.dp)
                    .focusProperties {
                        // Mit den Pfeilen nur nach oben heraus – links/rechts/unten bleibt der Fokus am
                        // Rand stehen. Andere Wechsel (z. B. Zurück → Tab-Leiste) bleiben erlaubt.
                        exit = {
                            when (it) {
                                FocusDirection.Left, FocusDirection.Right, FocusDirection.Down -> FocusRequester.Cancel
                                else -> FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup(),
                // Scroll-Position im Model: bleibt beim Bildschirmwechsel erhalten, damit die
                // zuletzt gewählte Kachel beim Zurückkommen noch gezeichnet ist.
                state = model.dockScroll,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                    val pkg = app.packageName
                    val requester = tileFocus.getOrPut(pkg) { FocusRequester() }
                    DisposableEffect(pkg) {
                        composedTiles += pkg
                        onDispose { composedTiles -= pkg }
                    }
                    AppTile(
                        app = app,
                        art = model.art[pkg],
                        isFavorite = false,
                        onClick = { model.launch(app) },
                        onToggleFavorite = { model.toggleFavorite(pkg) },
                        modifier = Modifier
                            .width(150.dp)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    model.lastDockPackage = pkg
                                    if (hoverPkg != pkg) dismissedPkg = null
                                    hoverPkg = pkg
                                }
                            }
                            .onGloballyPositioned { c ->
                                tileCenters[pkg] = c.positionInRoot().x + c.size.width / 2f
                            },
                        tileModifier = Modifier
                            // OK lange gedrückt (~0,7 s): Popup statt App öffnen. Den Rest des
                            // Tastendrucks schluckt der äußere Box-Handler (s. o.).
                            .onPreviewKeyEvent { e ->
                                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                                if (!ok || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val n = e.nativeKeyEvent
                                if (n.repeatCount > 0 && n.eventTime - n.downTime >= LONG_PRESS_MS &&
                                    !model.popupItems(pkg).isEmpty()
                                ) {
                                    longPress[0] = true
                                    dismissedPkg = null
                                    popupPkg = pkg
                                    popupWantsFocus = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusRequester(requester)
                            .then(if (!hasSide && index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        showLabel = false,
                        shimmer = if (!hasSide && index == 0) ({ introShimmer.value }) else null,
                    )
                }
            }
        }
    }

    // Popup über der Kachel, mittig zu ihr, am Rand gehalten; erst sichtbar, wenn vermessen.
    val shownPkg = popupPkg
    val popupApp = favorites.firstOrNull { it.packageName == shownPkg }
    val entries = shownPkg?.let { model.popupItems(it) }.orEmpty()
    if (popupApp != null && entries.isNotEmpty()) {
        val density = LocalDensity.current
        val gap = with(density) { 14.dp.toPx() }
        HistoryPopup(
            popupApp, entries, model, popupFirst,
            Modifier
                .onSizeChanged { popupSize = it }
                .onFocusChanged { popupFocused = it.hasFocus }
                .offset {
                    val center = (tileCenters[popupApp.packageName] ?: 0f) - rootOrigin.x
                    val x = (center - popupSize.width / 2f).coerceIn(0f, (rootWidth - popupSize.width).coerceAtLeast(0).toFloat())
                    val y = dockTop - rootOrigin.y - popupSize.height - gap
                    IntOffset(x.toInt(), y.toInt())
                }
                .graphicsLayer { alpha = if (popupSize.height == 0) 0f else 1f },
            onLeave = { closePopup(refocus = true) },
        )
    }
    // Popup weg (z. B. Verlauf leer) → nicht mehr als fokussiert gelten.
    LaunchedEffect(popupApp != null && entries.isNotEmpty()) {
        if (popupApp == null || entries.isEmpty()) popupFocused = false
    }
    }

    // Erneut ausgelöst, wenn das Kontrollzentrum schließt oder Home gedrückt wird.
    // Auch erneut, sobald Apps geladen sind – vorher gibt es weder Widget noch Favoriten als Ziel.
    // Beim Öffnen: Fokus kurz verzögert setzen und die Kachel einmal aufploppen lassen –
    // sonst ist sie schon groß, bevor das Bild überhaupt zu sehen ist, und man sucht den Fokus.
    // Auch bei jeder Rückkehr nach Lumen (Home, Zurück aus einer App).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(focusKey, resumes, hasSide, favorites.isNotEmpty()) {
        runCatching { firstFocus.requestFocus() }
        if (hasSide || favorites.isEmpty()) return@LaunchedEffect
        // Fokus sofort; kurz danach zieht einmal ein Lichtschimmer über die Kachel (wie bei tvOS).
        introShimmer.snapTo(0f)
        delay(INTRO_DELAY_MS)
        // Gleichmäßig, damit das Auge der Bewegung folgen kann.
        introShimmer.animateTo(1f, tween(INTRO_SHIMMER_MS, easing = LinearEasing))
    }
}

private const val INTRO_DELAY_MS = 450L
private const val INTRO_SHIMMER_MS = 1400
private const val LONG_PRESS_MS = 700L

internal fun greeting(ms: Long): String =
    when (Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour) {
        in 5..10 -> "Guten Morgen"
        in 11..16 -> "Guten Tag"
        in 17..21 -> "Guten Abend"
        else -> "Gute Nacht"
    }

@Composable
private fun ContinueWidget(app: AppEntry, model: LauncherModel, modifier: Modifier) {
    // Das Widget ist fast doppelt so groß wie eine Dock-Kachel → eigene, schärfere Fassung laden.
    val context = LocalContext.current
    var sharp by remember(app.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(app.packageName) {
        sharp = withContext(Dispatchers.IO) { AppRepository.loadArt(context, app, scale = 2) }
    }
    val art = sharp ?: model.art[app.packageName]
    // Meldet die App, was lief (Media-Session), zeigt das Widget den Inhalt statt nur der App.
    val content = model.continueContent()
    val contentArt = model.nowPlayingArt.takeIf { content != null }
    GlassTile(
        onClick = { if (content != null) model.resume(app) else model.launch(app) },
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        focusedScale = 1.06f,
    ) {
        // Radien konzentrisch: Widget 28 = Abstand 14 + Bild 14.
        Column(Modifier.padding(14.dp)) {
            Caption("Weiterschauen", Modifier.padding(start = 6.dp, top = 2.dp, bottom = 10.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    contentArt != null ->
                        Image(contentArt, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    art != null && art.isBanner ->
                        Image(art.forMode(model.dark), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    art != null -> Image(art.image, null, Modifier.size(64.dp))
                }
            }
            if (content != null) {
                // Serie oben (Anime-Name), Folge darunter; sonst Titel oben, Künstler o. Ä. darunter.
                val head = if (content.isSeries) content.subtitle!! else content.title
                val sub = if (content.isSeries) content.title else content.subtitle
                LText(head, 16.sp, Modifier.padding(start = 6.dp, top = 10.dp), FontWeight.SemiBold)
                LText(
                    listOfNotNull(app.label, content.episodeLabel, sub).joinToString(" · "), 13.sp,
                    Modifier.padding(start = 6.dp, top = 1.dp, bottom = 2.dp), FontWeight.Medium, alpha = 0.7f,
                )
            } else {
                LText(app.label, 17.sp, Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp), FontWeight.SemiBold)
            }
        }
    }
}
