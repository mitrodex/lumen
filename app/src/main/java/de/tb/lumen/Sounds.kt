package de.tb.lumen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dezentes Navigations-Feedback wie bei tvOS: ein kurzer, weicher „Tick“ beim Bewegen und ein
 * etwas vollerer Ton beim Auswählen. Die Töne werden einmal im Code erzeugt (keine Dateien)
 * und als statische AudioTracks vorgehalten – Abspielen kostet praktisch nichts.
 */
object Sounds {
    private const val RATE = 44_100

    private val move by lazy { track(tone(freq = 1_900.0, ms = 14, decay = 380.0, gain = 0.10)) }
    private val select by lazy { track(tone(freq = 1_150.0, ms = 34, decay = 120.0, gain = 0.16, overtone = 1.5)) }
    private var lastMove = 0L

    fun move() {
        val now = SystemClock.elapsedRealtime()
        // Gedrückt halten: nicht jedes Wiederholungs-Event klicken lassen.
        if (now - lastMove < 70) return
        lastMove = now
        play(move)
    }

    fun select() = play(select)

    private fun play(t: AudioTrack?) {
        t ?: return
        runCatching {
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    /** Sinus mit exponentiellem Ausklingen (und optional leiser Oberton für mehr Fülle). */
    private fun tone(freq: Double, ms: Int, decay: Double, gain: Double, overtone: Double = 0.0): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = exp(-t * decay) * (1 - exp(-t * 4_000)) // weicher Einsatz, kein Knacken
            var s = sin(2 * PI * freq * t)
            if (overtone > 0) s = 0.8 * s + 0.2 * sin(2 * PI * freq * overtone * t)
            (s * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun track(pcm: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply { write(pcm, 0, pcm.size) }
    }.getOrNull()
}
