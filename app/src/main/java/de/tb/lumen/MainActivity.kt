package de.tb.lumen

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.lang.ref.WeakReference
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import de.tb.lumen.ui.LocalWallpaper
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.tb.lumen.ui.AppsScreen
import de.tb.lumen.ui.ControlCenter
import de.tb.lumen.ui.DarkGlass
import de.tb.lumen.ui.HomeScreen
import de.tb.lumen.ui.LightGlass
import de.tb.lumen.ui.LocalDarkMode
import de.tb.lumen.ui.LocalGlass
import de.tb.lumen.ui.AnimatedWallpaper
import de.tb.lumen.ui.ForecastCard
import de.tb.lumen.ui.BackInterceptors
import de.tb.lumen.ui.ExpandedPlayer
import de.tb.lumen.ui.Blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import de.tb.lumen.ui.Category
import de.tb.lumen.ui.SettingsScreen
import de.tb.lumen.ui.TabFocus
import de.tb.lumen.ui.TopBar
import de.tb.lumen.ui.Wallpaper
import de.tb.lumen.ui.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { Home, Apps, Search, Settings }

class MainActivity : ComponentActivity() {

    private lateinit var model: LauncherModel
    private val screen = mutableStateOf(Screen.Home)
    private val controlOpen = mutableStateOf(false)
    private val homeFocus = mutableIntStateOf(0)
    private val tabs = TabFocus()
    /** Mit welcher Seite die Einstellungen aufgehen (Kontrollzentrum: WLAN/Bluetooth direkt). */
    private val settingsStart = mutableStateOf(Category.Appearance)
    private val weatherFocused = mutableStateOf(false)
    /** Große Player-Ansicht (Klick aufs Cover im „Jetzt läuft“-Widget). */
    private val musicExpanded = mutableStateOf(false)
    private val musicBackdrop = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    /** Startseite abfotografieren, weichzeichnen, dann die große Player-Ansicht darüber öffnen. */
    private fun expandMusic() {
        snapshot { shot ->
            musicBackdrop.value = shot?.let { Blur.of(it).asImageBitmap() }
            musicExpanded.value = true
        }
    }
    private var focusInTopBar = false

    private fun closeControl() {
        controlOpen.value = false
        homeFocus.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = LauncherModel.get(this)
        handleCity(intent)

        setContent {
            LaunchedEffect(Unit) {
                model.updateWeather()
                model.updateCalendar()
                while (true) {
                    delay(60_000 - System.currentTimeMillis() % 60_000)
                    model.tick()
                    model.updateWeather()
                    model.updateCalendar()
                }
            }
            val wallpaper = Wallpaper.get(model.phase, model.dark)

            CompositionLocalProvider(
                LocalGlass provides if (model.dark) DarkGlass else LightGlass,
                LocalDarkMode provides model.dark,
                LocalWallpaper provides wallpaper,
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Weicher Übergang bei Tageszeit- oder Dunkelmodus-Wechsel.
                    // Läuft AirPlay-Musik, pausiert der bewegte Hintergrund: der Stick braucht die
                    // Rechenzeit fürs Dekodieren, sonst kann die Wiedergabe stocken.
                    if (model.animatedBackground && model.music.now == null) {
                        AnimatedWallpaper(model.phase, model.dark)
                    } else {
                        Crossfade(wallpaper, animationSpec = tween(1200), label = "wallpaper") { WallpaperImage(it) }
                    }

                    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 26.dp)) {
                        TopBar(
                            screen = screen.value,
                            timeText = if (screen.value == Screen.Home) null else formatTime(model.now),
                            tabs = tabs,
                            onSelect = { screen.value = it },
                            onControl = { controlOpen.value = true },
                            onFocusInBar = { focusInTopBar = it },
                            weather = model.weather,
                            onWeatherFocus = { weatherFocused.value = it },
                        )
                        Spacer(Modifier.height(22.dp))
                        Crossfade(screen.value, Modifier.weight(1f), animationSpec = tween(200), label = "screen") { s ->
                            when (s) {
                                Screen.Home -> HomeScreen(
                                    model, homeFocus.intValue,
                                    hideClock = weatherFocused.value,
                                    onExpandMusic = ::expandMusic,
                                ) { tabs.focus(Screen.Home) }
                                Screen.Apps -> AppsScreen(model, search = false)
                                Screen.Search -> AppsScreen(model, search = true)
                                Screen.Settings -> SettingsScreen(model, settingsStart.value)
                            }
                        }
                    }

                    // Wetter-Vorschau: klappt unter der Wetter-Pille oben links auf (über dem Inhalt).
                    val w = model.weather
                    AnimatedVisibility(
                        visible = weatherFocused.value && screen.value == Screen.Home && w != null && w.hourly.isNotEmpty(),
                        modifier = Modifier.padding(start = 48.dp, top = 26.dp + 52.dp + 12.dp),
                        enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { -it / 5 },
                        exit = fadeOut(tween(140)),
                    ) {
                        if (w != null) ForecastCard(w, w.hourly)
                    }

                    val music = model.music.now
                    if (music == null) musicExpanded.value = false
                    AnimatedVisibility(
                        visible = musicExpanded.value && music != null,
                        enter = fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.92f),
                        exit = fadeOut(tween(200)),
                    ) {
                        // Beim Schließen den letzten Stand weiter zeigen, bis ausgeblendet ist.
                        val last = remember { mutableStateOf(music) }
                        if (music != null) last.value = music
                        last.value?.let { ExpandedPlayer(it, model.music, musicBackdrop.value) }
                    }
                    // Nach dem Schließen zurück aufs Widget (sonst landet der Fokus irgendwo).
                    LaunchedEffect(musicExpanded.value) {
                        if (!musicExpanded.value && music != null) {
                            // Erst nach der Ausblend-Animation – vorher hält die Ansicht noch den Fokus.
                            delay(260)
                            homeFocus.intValue++
                        }
                    }

                    if (controlOpen.value) {
                        ControlCenter(
                            model,
                            onClose = ::closeControl,
                            onOpenSettings = { page ->
                                controlOpen.value = false
                                settingsStart.value = page
                                screen.value = Screen.Settings
                            },
                        )
                    }
                }
            }

        }
    }

    /** Zurück wie bei tvOS: erst hoch in die Tab-Leiste, von dort zur Startseite. */
    private fun handleBack() {
        when {
            musicExpanded.value -> musicExpanded.value = false
            BackInterceptors.handle() -> Unit
            controlOpen.value -> closeControl()
            !focusInTopBar -> tabs.focus(screen.value)
            screen.value != Screen.Home -> screen.value = Screen.Home
            // In der Leiste auf der Startseite tut Zurück nichts.
        }
    }

    override fun onResume() {
        super.onResume()
        HomeWatcherService.instance?.hideCover()
        SystemSettings.selfHeal(this)
        model.tick()
        model.loadNowPlaying()
        model.refresh(lifecycleScope)
        lifecycleScope.launch { model.updateCalendar(Calendar.ON_RETURN_MS) }
    }

    /** Home-Taste (über den HomeWatcherService) -> zurück zur Startseite. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen.value = Screen.Home
        closeControl()
        handleCity(intent)
    }

    /** Wetter-Ort setzen: adb shell am start -n de.tb.lumen/.MainActivity --es lumen.city "Berlin" */
    private fun handleCity(intent: Intent?) {
        val city = intent?.getStringExtra(EXTRA_CITY)?.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launch { model.setWeatherPlace(city) }
    }

    /** Tick beim Bewegen (Taste runter), vollerer Ton beim Auswählen und Zurück (Taste hoch). */
    private fun playKeySound(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (e.action == KeyEvent.ACTION_DOWN) Sounds.move()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK ->
                if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) Sounds.select()
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = WeakReference(this)
    }

    override fun onStop() {
        if (foreground?.get() === this) foreground = null
        super.onStop()
    }

    /**
     * Bild des aktuellen Bildschirms (GPU-genau per PixelCopy). Der HomeWatcherService legt es bei
     * Home als Abdeckung hin, wenn Lumen schon vorne ist – dann sieht man Lumen einfach weiter,
     * statt dass die Oberfläche kurz verschwindet.
     */
    fun snapshot(onResult: (Bitmap?) -> Unit) {
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return onResult(null)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(window, bmp, { r -> onResult(if (r == PixelCopy.SUCCESS) bmp else null) }, Handler(Looper.getMainLooper()))
        }.onFailure { onResult(null) }
    }

    companion object {
        private const val EXTRA_CITY = "lumen.city"

        /** Sichtbare MainActivity (zwischen onStart und onStop), sonst null. */
        @Volatile
        var foreground: WeakReference<MainActivity>? = null
            private set
    }

    /**
     * Menü-Taste öffnet/schließt das Kontrollzentrum. Zurück wird hier abgefangen, bevor Compose
     * es als „Fokus verlässt die Gruppe“ deutet und den Fokus irgendwohin schiebt.
     * (Eine offene Bildschirmtastatur bekommt Zurück trotzdem zuerst.)
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (model.sounds) playKeySound(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleBack()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (controlOpen.value) closeControl() else controlOpen.value = true
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@androidx.compose.runtime.Composable
private fun WallpaperImage(image: ImageBitmap) {
    Image(
        image, null, Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.High,
    )
}
