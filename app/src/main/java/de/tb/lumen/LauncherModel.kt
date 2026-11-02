package de.tb.lumen

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.annotation.Keep
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.tb.lumen.ui.Wallpaper
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DEFAULT_FAVORITES = listOf(
    "com.netflix.ninja",
    "com.amazon.firebat", // Prime Video
    "com.disney.disneyplus",
    "com.amazon.firetv.youtube",
    "com.spotify.tv.android",
    "com.zdf.android.mediathek",
    "de.swr.ard.avp.mobile.android.amazon",
    "de.prosiebensat1digital.seventv",
    "de.cbc.tvnow.firetv",
    "de.telekom.magentatv.firetv",
)

/**
 * Ein Model für den ganzen Prozess: Fire OS wirft Lumens Activity bei Speichermangel oft weg,
 * der Prozess lebt wegen der Dienste aber weiter. So sind Apps, Banner und Favoriten beim
 * Neuaufbau sofort da statt eines leeren Docks.
 */
/** Eine Zeile im Weiterschauen-Popup über den Favoriten. */
data class PopupItem(
    val key: String,
    val head: String,
    val sub: String?,
    val art: ImageBitmap?,
    val open: () -> Unit,
)

class LauncherModel private constructor(private val context: Context) {
    private val prefs = Appearance.prefs(context)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    val art = mutableStateMapOf<String, AppArt>()
    var favorites by mutableStateOf(readFavorites() ?: emptyList())
        private set
    var lastLaunched by mutableStateOf(prefs.getString(KEY_LAST, null))
        private set

    /** Zuletzt gespielter Inhalt (vom [MediaWatcher]), samt Bild. */
    var nowPlaying by mutableStateOf<NowPlayingInfo?>(null)
        private set
    var nowPlayingArt by mutableStateOf<ImageBitmap?>(null)
        private set

    /** „Zuletzt geschaut“ je App (für das Popup über den Favoriten), neueste zuerst. */
    var history by mutableStateOf<Map<String, List<HistoryEntry>>>(emptyMap())
        private set
    private val historyArt = mutableMapOf<String, ImageBitmap?>()

    // Der MediaWatcher schreibt in dieselben Prefs – so aktualisiert sich das Widget sofort.
    // Android hält Listener nur schwach – @Keep, sonst optimiert R8 das Feld weg und der Listener
    // wird eingesammelt (das Widget aktualisierte sich dann erst nach einem Neustart).
    @field:Keep
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (NowPlaying.isKey(key)) loadNowPlaying()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadNowPlaying()
    }

    /** Auch beim Zurückkehren auf die Startseite – falls eine Änderung durchgerutscht ist. */
    fun loadNowPlaying() {
        nowPlaying = NowPlaying.load(context)
        nowPlayingArt = NowPlaying.loadArt(context)?.asImageBitmap()
        lastLaunched = prefs.getString(KEY_LAST, null)
        // Nur Inhalte, die sich per Direktlink genau dort öffnen lassen (derzeit Crunchyroll) –
        // alles andere würde nur die App starten, das kann die Kachel selbst.
        history = History.load(context)
            .mapValues { (_, list) -> list.filter { NowPlaying.canDeepLink(it.info) } }
            .filterValues { it.isNotEmpty() }
        historyArt.clear()
    }

    // Ändern sich die „Weiterschauen“-Empfehlungen (Netflix & Co.), zeichnet sich das Popup neu.
    private var recsVersion by mutableIntStateOf(0)
    private val recArt = mutableMapOf<String, ImageBitmap?>()

    init {
        Recommendations.onChange = {
            recArt.clear()
            recsVersion++
        }
    }

    /**
     * Einträge fürs Weiterschauen-Popup einer Favoriten-App – nur solche, die direkt in den
     * Inhalt springen: Empfehlungen der App mit eigenem Link (Netflix), dann der eigene Verlauf
     * mit Direktlink (Crunchyroll).
     */
    fun popupItems(pkg: String): List<PopupItem> {
        @Suppress("UNUSED_VARIABLE") val v = recsVersion
        val app = apps.firstOrNull { it.packageName == pkg } ?: return emptyList()
        val fromApp = Recommendations.forPackage(pkg).map { r ->
            PopupItem(
                key = r.key, head = r.head, sub = r.sub,
                art = recArt.getOrPut(r.key) { r.art?.asImageBitmap() },
            ) { if (runCatching { r.intent.send() }.isFailure) launch(app) }
        }
        val fromHistory = history[pkg].orEmpty().mapIndexed { i, e ->
            val info = e.info
            PopupItem(
                key = "h:${info.packageName}:${info.subtitle}:${info.title}",
                head = if (info.isSeries) info.subtitle ?: info.title else info.title,
                sub = listOfNotNull(info.episodeLabel, if (info.isSeries) info.title else info.subtitle)
                    .joinToString(" · ").ifEmpty { null },
                art = historyArt(e),
            ) { openHistory(app, e, latest = i == 0) }
        }
        return (fromApp + fromHistory).take(POPUP_MAX)
    }

    fun historyArt(entry: HistoryEntry): ImageBitmap? {
        val name = entry.artFile ?: return null
        return historyArt.getOrPut(name) { History.loadArt(context, entry)?.asImageBitmap() }
    }

    /**
     * Eintrag aus dem Popup öffnen: per Direktlink in genau diese Folge (Crunchyroll), beim
     * neuesten Eintrag notfalls über die Session; sonst einfach die App.
     */
    fun openHistory(app: AppEntry, entry: HistoryEntry, latest: Boolean) {
        val ok = NowPlaying.resume(context, entry.info, app.packageName, allowSession = latest)
        if (!ok) launch(app)
    }

    /** Inhalt zum „Weiterschauen“ – nur, wenn er zur zuletzt genutzten App gehört. */
    fun continueContent(): NowPlayingInfo? = nowPlaying?.takeIf { it.packageName == lastLaunched }

    /** Direkt in den Player zurück, sonst einfach die App öffnen. */
    fun resume(app: AppEntry) {
        if (!NowPlaying.resume(context, nowPlaying, app.packageName)) launch(app)
    }

    /** Zuletzt fokussierte Favoriten-Kachel und Scroll-Stand des Docks (Einstieg per Pfeil runter). */
    var lastDockPackage: String? = null
    val dockScroll = LazyListState()

    /** Aktuelle Zeit, minütlich aktualisiert über [tick]. */
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var dark by mutableStateOf(Appearance.isDark(prefs, now))
        private set
    var phase by mutableStateOf(Wallpaper.phase(now))
        private set

    var weather by mutableStateOf(Weather.cached(context))
        private set

    /** AirPlay-Musik vom iPhone (für das „Jetzt läuft“-Widget). */
    val music = AirPlayMusic(context).also { it.start() }

    /** Navigations-Töne (Schalter im Kontrollzentrum). */
    var sounds by mutableStateOf(prefs.getBoolean(KEY_SOUNDS, true))
        private set

    fun toggleSounds() {
        sounds = !sounds
        prefs.edit().putBoolean(KEY_SOUNDS, sounds).apply()
    }

    /** Langsam wandernde Farbflächen auf der Startseite (Schalter in den Einstellungen). */
    var animatedBackground by mutableStateOf(prefs.getBoolean(KEY_ANIMATED_BG, true))
        private set

    fun toggleAnimatedBackground() {
        animatedBackground = !animatedBackground
        prefs.edit().putBoolean(KEY_ANIMATED_BG, animatedBackground).apply()
    }

    fun tick() {
        now = System.currentTimeMillis()
        dark = Appearance.isDark(prefs, now)
        phase = Wallpaper.phase(now)
    }

    /** Höchstens alle 30 Minuten neu laden; ohne Ort passiert nichts. */
    suspend fun updateWeather(force: Boolean = false) {
        val w = withContext(Dispatchers.IO) { Weather.refreshIfDue(context, force) }
        if (w != null) weather = w
    }

    /** Termine dieser Woche (aus dem Zwischenspeicher, regelmäßig neu aus iCloud). */
    var calendar by mutableStateOf(Calendar.cached(context))
        private set

    /** Neu laden, falls der Stand älter als [maxAgeMs] ist (ohne Angabe: Calendar-Standard). */
    suspend fun updateCalendar(maxAgeMs: Long? = null) {
        val events = withContext(Dispatchers.IO) {
            if (maxAgeMs != null) Calendar.refreshIfDue(context, maxAgeMs) else Calendar.refreshIfDue(context)
        }
        if (events != null) calendar = events
    }

    /** Ort per Name setzen (z. B. per ADB: am start … --es lumen.city Berlin). */
    suspend fun setWeatherPlace(name: String) {
        if (withContext(Dispatchers.IO) { Weather.setPlace(context, name) }) updateWeather(force = true)
    }

    fun toggleDark() {
        Appearance.toggle(prefs, System.currentTimeMillis())
        tick()
    }

    fun refresh(scope: CoroutineScope) {
        scope.launch {
            val list = withContext(Dispatchers.IO) { AppRepository.loadApps(context) }
            apps = list
            if (readFavorites() == null) {
                val installed = list.map { it.packageName }.toSet()
                saveFavorites(DEFAULT_FAVORITES.filter { it in installed })
            }
            for (app in list) {
                if (app.packageName in art) continue
                val loaded = withContext(Dispatchers.IO) { AppRepository.loadArt(context, app) }
                if (loaded != null) art[app.packageName] = loaded
            }
        }
    }

    fun favoriteApps(): List<AppEntry> {
        val byPackage = apps.associateBy { it.packageName }
        return favorites.mapNotNull { byPackage[it] }
    }

    fun continueApp(): AppEntry? = lastLaunched?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }

    fun toggleFavorite(pkg: String) {
        saveFavorites(if (pkg in favorites) favorites - pkg else favorites + pkg)
    }

    fun launch(app: AppEntry) {
        if (AppRepository.launch(context, app)) {
            lastLaunched = app.packageName
            prefs.edit().putString(KEY_LAST, app.packageName).apply()
        }
    }

    private fun readFavorites(): List<String>? =
        prefs.getString(KEY_FAVORITES, null)?.split(',')?.filter { it.isNotBlank() }

    private fun saveFavorites(list: List<String>) {
        favorites = list
        prefs.edit().putString(KEY_FAVORITES, list.joinToString(",")).apply()
    }

    companion object {
        @Volatile
        private var instance: LauncherModel? = null

        fun get(context: Context): LauncherModel =
            instance ?: synchronized(this) { instance ?: LauncherModel(context.applicationContext).also { instance = it } }

        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SOUNDS = "sounds"
        private const val KEY_ANIMATED_BG = "animated_bg"
        private const val KEY_LAST = NowPlaying.KEY_LAST_LAUNCHED
        private const val POPUP_MAX = 4
    }
}
