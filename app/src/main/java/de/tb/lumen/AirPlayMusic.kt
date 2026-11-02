package de.tb.lumen

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Was gerade per AirPlay als Musik läuft. */
data class MusicNow(
    val title: String,
    val artist: String?,
    val art: ImageBitmap?,
    /** Winzige Fassung des Covers – groß gezogen ergibt das eine weiche Farbfläche (Pseudo-Blur). */
    val artTiny: ImageBitmap?,
    val durationMs: Long,
    private val positionMs: Long,
    private val positionAt: Long,
    private val speed: Float,
    val playing: Boolean,
) {
    /** Aktuelle Position, hochgerechnet seit der letzten Meldung. */
    fun positionNow(): Long {
        val p = if (playing) positionMs + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Liest live die Media-Session von „AirPlay Server“ (Lumen darf das dank Benachrichtigungs-
 * Zugriff) – für das „Jetzt läuft“-Widget auf der Startseite. Steuert auch Play/Pause/Skip.
 */
class AirPlayMusic(private val context: Context) {
    var now by mutableStateOf<MusicNow?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private val me = ComponentName(context, MediaWatcher::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() = attach(null)
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list?.firstOrNull { it.packageName == AirPlayKeeper.PACKAGE })
    }

    fun start() {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessions, me, handler)
            attach(msm.getActiveSessions(me).firstOrNull { it.packageName == AirPlayKeeper.PACKAGE })
        }
    }

    private fun attach(c: MediaController?) {
        if (c?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, handler)
        update()
    }

    private fun update() {
        val c = controller
        val m = c?.metadata
        val s = c?.playbackState
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        // Nur Musik zählt: beim Spiegeln hat die Session keinen Titel. Gestoppt = Widget weg.
        if (c == null || title == null || s == null || s.state == PlaybackState.STATE_STOPPED || s.state == PlaybackState.STATE_NONE) {
            now = null
            return
        }
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        now = MusicNow(
            title = title,
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            art = art?.asImageBitmap(),
            artTiny = art?.let { android.graphics.Bitmap.createScaledBitmap(it, 10, 10, true).asImageBitmap() },
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = s.position,
            positionAt = s.lastPositionUpdateTime,
            speed = s.playbackSpeed,
            playing = s.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun playPause() {
        val t = controller?.transportControls ?: return
        if (now?.playing == true) t.pause() else t.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
}
