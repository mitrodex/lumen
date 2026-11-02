package de.tb.lumen

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Liest die Media-Sessions anderer Apps mit (dafür muss Lumen Benachrichtigungs-Zugriff haben –
 * nur so gibt Android die Sessions heraus) und merkt sich Titel und Bild des zuletzt gespielten
 * Inhalts für das „Weiterschauen“-Widget.
 *
 * Funktioniert nur bei Apps, die Metadaten melden. Netflix tut das auf Fire OS nicht.
 * Freischalten: adb shell cmd notification allow_listener de.tb.lumen/de.tb.lumen.MediaWatcher
 */
class MediaWatcher : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    @Volatile
    private var lastKey: String? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    override fun onListenerConnected() {
        // „Weiterschauen“-Empfehlungen (Netflix & Co.), die schon da sind.
        runCatching { activeNotifications?.forEach { Recommendations.posted(this, it) } }
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        val me = ComponentName(this, MediaWatcher::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
            attach(msm.getActiveSessions(me))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { Recommendations.posted(this, sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Recommendations.removed(sbn)
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(sessionsListener)
        watched.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        watched.clear()
    }

    private fun attach(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach { t -> watched.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) } }
        for (c in controllers) {
            if (c.sessionToken in watched || c.packageName in IGNORED) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = capture(c)
                override fun onPlaybackStateChanged(state: PlaybackState?) = capture(c)
            }
            c.registerCallback(cb, handler)
            watched[c.sessionToken] = c to cb
            capture(c, initial = true)
        }
    }

    private fun capture(c: MediaController, initial: Boolean = false) {
        if (!NowPlaying.CAPTURE) return
        val state = c.playbackState
        if (state?.state != PlaybackState.STATE_PLAYING) return
        if (c.packageName == PrimeVideo.PACKAGE) {
            capturePrime(c)
            return
        }
        // Beim Verbinden melden manche Apps (YouTube) noch „spielt“, obwohl sie längst im Hintergrund
        // liegen – solche alten Stände dürfen den echten letzten Inhalt nicht überschreiben.
        if (initial && SystemClock.elapsedRealtime() - state.lastPositionUpdateTime > STALE_MS) return
        val m = c.metadata ?: return
        val title = (m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_TITLE))
            ?.takeIf { it.isNotBlank() } ?: return
        // Serie/Künstler: Apps nutzen dafür verschiedene Felder. Crunchyroll z. B. legt den Folgentitel
        // in DISPLAY_TITLE und den Anime-Namen in TITLE ab – deshalb TITLE zuerst (falls anders).
        val subtitle = listOf(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_WRITER,
            MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
        ).firstNotNullOfOrNull { k -> m.getString(k)?.takeIf { it.isNotBlank() && it != title } }
            ?: m.description?.subtitle?.toString()?.takeIf { it.isNotBlank() && it != title }

        val key = "${c.packageName}|$title|$subtitle"
        if (key == lastKey) return
        lastKey = key

        // Disney+ & Co.: Serie im Titel, „S8:F9 Folgentitel“ im Untertitel.
        val numbered = subtitle?.let(::parseNumberedEpisode)
        val parsed = if (numbered != null) {
            NowPlayingInfo(
                c.packageName, numbered.title.ifBlank { str(R.string.episode_n, numbered.episode) }, title, System.currentTimeMillis(),
                isSeries = true, season = numbered.season, episode = numbered.episode,
            )
        } else {
            // Crunchyroll: kam der Untertitel aus TITLE, ist es der Serienname (Folge steht in DISPLAY_TITLE).
            val isSeries = subtitle != null && subtitle == m.getString(MediaMetadata.METADATA_KEY_TITLE)
            NowPlayingInfo(c.packageName, title, subtitle, System.currentTimeMillis(), isSeries)
        }
        val first = parsed.copy(mediaId = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() })
        val sessionActivity = c.sessionActivity
        io.execute {
            // Erst sofort speichern (Home direkt nach Pause), dann ggf. mit TMDB-Daten ergänzen.
            val appArt = runCatching { artFrom(m)?.let(::scaled) }.getOrNull()
            NowPlaying.save(applicationContext, first, appArt, sessionActivity)
            // Disney+: Direktlink auf die Serie/den Film über JustWatch nachschlagen.
            val info = if (first.packageName == DisneyPlus.PACKAGE && first.mediaId == null) {
                val name = if (first.isSeries) first.subtitle else first.title
                val id = name?.let { DisneyPlus.lookup(it, first.isSeries) }
                if (id != null && key == lastKey) first.copy(mediaId = id).also {
                    NowPlaying.save(applicationContext, it, appArt, sessionActivity)
                } else first
            } else first
            if (!info.isSeries || (appArt != null && info.season != null)) return@execute
            // Serie: Staffel/Folge (und Standbild, falls die App keins liefert) bei TMDB nachschlagen.
            val tmdbKey = Config.tmdbKey(applicationContext)
            val ep = runCatching { Episodes.lookup(tmdbKey, info.subtitle!!, info.title, info.season, info.episode) }.getOrNull()
                ?: return@execute
            if (key != lastKey) return@execute // inzwischen läuft etwas anderes
            val art = appArt ?: ep.still?.let(::scaled)
            NowPlaying.save(applicationContext, info.copy(season = ep.season, episode = ep.episode), art, sessionActivity)
        }
    }

    /** Prime Video: nur die Inhalts-ID kommt – Titel und Bild holt [PrimeVideo] nach. */
    private fun capturePrime(c: MediaController) {
        val gti = c.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { PrimeVideo.isId(it) } ?: return
        val key = "${c.packageName}|$gti"
        if (key == lastKey) return
        lastKey = key
        io.execute {
            val t = runCatching { PrimeVideo.lookup(gti) }.getOrNull() ?: return@execute
            if (key != lastKey) return@execute
            val info = NowPlayingInfo(
                c.packageName,
                title = t.season?.let { str(R.string.season_n, it) } ?: t.name,
                subtitle = t.name.takeIf { t.season != null },
                time = System.currentTimeMillis(),
                isSeries = t.season != null,
                mediaId = gti,
            )
            NowPlaying.save(applicationContext, info, t.image?.let(::scaled), null)
        }
    }

    private fun artFrom(m: MediaMetadata): Bitmap? {
        for (k in listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
            m.getBitmap(k)?.let { return it }
        }
        for (k in listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
            val uri = m.getString(k)?.takeIf { it.isNotBlank() } ?: continue
            loadUri(Uri.parse(uri))?.let { return it }
        }
        return null
    }

    private fun loadUri(uri: Uri): Bitmap? = runCatching {
        when (uri.scheme) {
            "http", "https" -> {
                val conn = URL(uri.toString()).openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }.also { conn.disconnect() }
            }
            else -> contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    private fun scaled(b: Bitmap): Bitmap {
        val maxW = 640
        if (b.width <= maxW) return b
        return Bitmap.createScaledBitmap(b, maxW, b.height * maxW / b.width, true)
    }

    private companion object {
        const val STALE_MS = 10 * 60_000L

        // Alexa, Lumen selbst und Amazons Bildschirmschoner (meldet beim Abspielen eine Session).
        val IGNORED = setOf("com.amazon.vizzini", "de.tb.lumen", "com.amazon.ftv.screensaver", "com.amazon.bueller.photos", AirPlayKeeper.PACKAGE)
    }
}
