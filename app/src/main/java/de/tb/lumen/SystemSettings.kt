package de.tb.lumen

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Systemeinstellungen, die Lumen selbst setzt. Braucht WRITE_SECURE_SETTINGS – einmalig per ADB:
 *   adb shell pm grant de.tb.lumen android.permission.WRITE_SECURE_SETTINGS
 * Ohne die Berechtigung sind alle Setter wirkungslos ([canWrite] = false), Lesen geht immer.
 */
object SystemSettings {
    private const val AMAZON_SCREENSAVER = "com.amazon.ftv.screensaver/.app.services.ScreensaverService"

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun lumenDream(context: Context) = ComponentName(context, LumenDream::class.java).flattenToString()

    // --- Bildschirmschoner & Ruhezustand ---------------------------------------------------

    fun isLumenScreensaver(context: Context) =
        Settings.Secure.getString(context.contentResolver, "screensaver_components") == lumenDream(context)

    fun setLumenScreensaver(context: Context, on: Boolean) = write(context) {
        Settings.Secure.putString(it, "screensaver_components", if (on) lumenDream(context) else AMAZON_SCREENSAVER)
    }

    /** Minuten bis zum Bildschirmschoner. */
    fun screensaverDelayMin(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 300_000) / 60_000

    fun setScreensaverDelayMin(context: Context, min: Int) = write(context) {
        val ms = min * 60_000
        Settings.System.putInt(it, Settings.System.SCREEN_OFF_TIMEOUT, ms)
        // Fire OS liest zusätzlich diesen Wert.
        Settings.Secure.putInt(it, "contextual_screen_off_timeout", ms)
    }

    /** Minuten bis zum Ruhezustand, -1 = nie. */
    fun sleepMin(context: Context): Int {
        val ms = Settings.Secure.getInt(context.contentResolver, "sleep_timeout", 1_200_000)
        return if (ms < 0) -1 else ms / 60_000
    }

    fun setSleepMin(context: Context, min: Int) = write(context) {
        Settings.Secure.putInt(it, "sleep_timeout", if (min < 0) -1 else min * 60_000)
    }

    // --- Animationen ---------------------------------------------------------------------

    private val animKeys = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    fun animationScale(context: Context) =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)

    fun setAnimationScale(context: Context, scale: Float) = write(context) { cr ->
        animKeys.forEach { Settings.Global.putFloat(cr, it, scale) }
    }

    // --- ADB -----------------------------------------------------------------------------

    fun adbEnabled(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun setAdbEnabled(context: Context, on: Boolean) = write(context) {
        Settings.Global.putInt(it, Settings.Global.ADB_ENABLED, if (on) 1 else 0)
    }

    // --- Selbstheilung -------------------------------------------------------------------

    /**
     * Trägt Lumens Dienste wieder ein, falls Fire OS sie entfernt hat (z. B. nach force-stop
     * oder Update). Wird beim Start, nach Updates und nach dem Booten aufgerufen.
     */
    fun selfHeal(context: Context) {
        if (!canWrite(context)) return
        val cr = context.contentResolver
        val home = ComponentName(context, HomeWatcherService::class.java).flattenToString()
        val a11y = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (home !in a11y.split(':')) {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, listOf(a11y, home).filter { it.isNotBlank() }.joinToString(":"))
        }
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
        // Lumen als (leerer) Sprachassistent: hebt die 5-s-Sperre nach Home auf – nur, wenn kein
        // anderer Assistent (z. B. Alexa) eingetragen ist.
        if (Settings.Secure.getString(cr, "voice_interaction_service").isNullOrBlank()) {
            Settings.Secure.putString(cr, "voice_interaction_service", ComponentName(context, LumenVoiceService::class.java).flattenToString())
            Settings.Secure.putString(cr, "voice_recognition_service", ComponentName(context, LumenRecognitionService::class.java).flattenToString())
        }
        val media = ComponentName(context, MediaWatcher::class.java).flattenToString()
        val listeners = Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
        if (media !in listeners.split(':')) {
            Settings.Secure.putString(cr, "enabled_notification_listeners", listOf(listeners, media).filter { it.isNotBlank() }.joinToString(":"))
        }
    }

    private inline fun write(context: Context, block: (android.content.ContentResolver) -> Unit): Boolean {
        if (!canWrite(context)) return false
        return runCatching { block(context.contentResolver) }.isSuccess
    }
}
