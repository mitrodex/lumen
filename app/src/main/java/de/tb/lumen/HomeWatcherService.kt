package de.tb.lumen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import de.tb.lumen.ui.HomeCover

/**
 * Ersetzt die Amazon-Startseite: Kommt deren HomeActivity nach vorne (Home-Taste, Zurück aus
 * einer App), wird Lumen gestartet.
 *
 * Fire OS lässt Apps die Home-Taste weder abfangen noch per Broadcast sehen (alles nur mit
 * Amazon-Systemrechten). Android hält nach Home Activity-Starts aus dem Hintergrund ~5 s
 * zurück; damit man die Amazon-Seite so lange nicht sieht, liegt eine [HomeCover] darüber.
 *
 * Fire-Einstellungen: Sie sind ein Tab der Amazon-Startseite. Während einer
 * Einstellungs-Sitzung ([beginSettingsSession]) bleibt die Amazon-Seite stehen. Weil Home
 * dort kein Event erzeugt, prüft der Dienst regelmäßig, ob der Einstellungs-Tab noch zu
 * sehen ist – verschwindet er, hat der Nutzer Home gedrückt.
 *
 * Notausgang: Mehr als [MAX_REDIRECTS] Umleitungen in 10 s pausieren alles für eine Minute.
 */
class HomeWatcherService : AccessibilityService() {

    private val redirects = ArrayDeque<Long>()
    private var pausedUntil = 0L
    private var lastRedirect = 0L
    private var settingsSession = false
    private var settingsSessionEnd = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private val coverTimeout = Runnable { hideCover() }
    private val settingsCheck = Runnable { checkSettingsStillOpen() }

    // Fire OS loggt die Warnung pro Home-Druck zweimal – einmal reagieren genügt.
    private var lastLogHome = 0L
    private val homeKeyLog = HomeKeyLog(
        onHome = {
            handler.post {
                val now = SystemClock.elapsedRealtime()
                if (now - lastLogHome < 800) return@post
                lastLogHome = now
                onHomeKeyFromLog()
            }
        },
        // Apple-Geräte verbinden sich oft nur für Millisekunden (Abfrage) oder lange vor dem
        // Abspielen – eine Verbindung allein öffnet deshalb nichts mehr.
        onAirPlayConnected = {},
        // Bildschirm wird gespiegelt: AirPlay-App nach vorne (Musik-Bildschirm ggf. schließen).
        onMirrorStarted = { handler.post { openAirPlay.run() } },
        // Nur Musik: die (schwarze) AirPlay-App soll nicht vorne stehen – Lumen zeigt die Musik
        // als Widget auf der Startseite (Daten aus der Media-Session).
        onAudioOnly = {
            handler.post {
                if (rootInActiveWindow?.packageName == AirPlayKeeper.PACKAGE) performGlobalAction(GLOBAL_ACTION_BACK)
            }
        },
        // Getrennt: AirPlay-App schließen → zurück, wo man vorher war.
        onAirPlayEnded = { handler.postDelayed({ closeAirPlayIfInFront() }, 700) },
    )

    // Die App selbst öffnet sich nicht mehr bei jeder Verbindung (launch_on_connect = false).
    private val openAirPlay = Runnable {
        if (rootInActiveWindow?.packageName == AirPlayKeeper.PACKAGE) return@Runnable
        val launch = packageManager.getLeanbackLaunchIntentForPackage(AirPlayKeeper.PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(AirPlayKeeper.PACKAGE)
            ?: return@Runnable
        runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
    }

    private var lastAirPlayClose = 0L

    /** Höchstens einmal pro 2 s Zurück drücken – sonst träfe ein zweites Zurück die App dahinter. */
    private fun closeAirPlayIfInFront() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAirPlayClose < 2_000) return
        if (rootInActiveWindow?.packageName == AirPlayKeeper.PACKAGE) {
            lastAirPlayClose = now
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // AirPlay-Empfänger bereithalten: sofort und dann alle 2 Minuten prüfen (siehe AirPlayKeeper).
    private val airPlayCheck = object : Runnable {
        override fun run() {
            Thread { AirPlayKeeper.ensureRunning(applicationContext) }.start()
            handler.postDelayed(this, 120_000)
        }
    }

    override fun onServiceConnected() {
        instance = this
        homeKeyLog.start()
        handler.post(airPlayCheck)
    }

    /**
     * Home erkannt, bevor die Amazon-Seite gezeichnet ist: Abdeckung sofort hinlegen, damit
     * nichts aufblitzt. Den Start von Lumen übernimmt das Fenster-Event wie gehabt – in den
     * Fire-Einstellungen gibt es keins, dort direkt umleiten.
     */
    private fun onHomeKeyFromLog() {
        if (SystemClock.elapsedRealtime() < pausedUntil) return
        if (settingsSession) {
            endSettingsSession()
            redirect()
        } else {
            // Ist Lumen schon vorne, dient ein Bild von Lumen selbst als Abdeckung – so bleibt
            // Lumen einfach sichtbar statt kurz zum leeren Hintergrund zu flackern.
            val lumen = MainActivity.foreground?.get()
            if (lumen != null) lumen.snapshot { showCover(it) } else showCover()
            // Kommt keine Amazon-Seite (z. B. Home im Bildschirmschoner), Abdeckung wieder weg.
            handler.postDelayed({ if (lastRedirect < lastLogHome) hideCover() }, 1_500)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        homeKeyLog.stop()
        handler.removeCallbacks(airPlayCheck)
        handler.removeCallbacks(openAirPlay)
        endSettingsSession()
        hideCover()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        when (event.packageName) {
            packageName -> endSettingsSession()
            AppRepository.AMAZON_LAUNCHER ->
                if (!settingsSession && event.className?.contains("HomeActivity") == true) redirect()
        }
    }

    fun beginSettingsSession() {
        settingsSession = true
        settingsSessionEnd = SystemClock.elapsedRealtime() + SETTINGS_SESSION_MAX_MS
        handler.removeCallbacks(settingsCheck)
        // Erste Prüfung erst, wenn der Einstellungs-Tab sicher geladen ist.
        handler.postDelayed(settingsCheck, 2_000)
    }

    private fun endSettingsSession() {
        settingsSession = false
        handler.removeCallbacks(settingsCheck)
    }

    private fun checkSettingsStillOpen() {
        if (!settingsSession) return
        if (SystemClock.elapsedRealtime() > settingsSessionEnd) {
            endSettingsSession()
            return
        }
        val root = rootInActiveWindow
        when (root?.packageName) {
            AppRepository.AMAZON_LAUNCHER -> {
                val stillSettings = SETTINGS_MARKERS.any { marker ->
                    root.findAccessibilityNodeInfosByText(marker).isNotEmpty()
                }
                if (!stillSettings) {
                    endSettingsSession()
                    redirect()
                    return
                }
            }
            // Unterseite der Einstellungen (Netzwerk, Bluetooth …) oder kurz kein Fenster: weiter warten.
            AppRepository.FIRE_SETTINGS, null -> Unit
            else -> {
                endSettingsSession()
                return
            }
        }
        handler.postDelayed(settingsCheck, SETTINGS_POLL_MS)
    }

    // --- Neustart ------------------------------------------------------------------------
    // Apps dürfen nicht selbst neu starten (REBOOT ist eine Systemberechtigung), und Amazons
    // Einstellungen reagieren weder auf Klick-Aktionen noch auf simulierte Berührungen – nur auf
    // die echte OK-Taste. Darum öffnet der Dienst „Mein Fire TV“, setzt den Fokus auf den Eintrag
    // mit exakt „Neu starten“ (nie auf „Werkseinstellungen“) und bittet um einen Druck auf OK.

    private var restartDeadline = 0L
    private val restartStep = Runnable { focusRestartEntry() }

    fun restartDevice() {
        restartDeadline = SystemClock.elapsedRealtime() + 6_000
        startActivity(
            Intent().setClassName(AppRepository.FIRE_SETTINGS, "${AppRepository.FIRE_SETTINGS}.tv.device.DeviceActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        handler.postDelayed(restartStep, 800)
    }

    private fun focusRestartEntry() {
        val root = rootInActiveWindow
        val entry = root?.takeIf { it.packageName == AppRepository.FIRE_SETTINGS }
            ?.let { r -> RESTART_LABELS.asSequence().flatMap { l -> r.findAccessibilityNodeInfosByText(l).orEmpty().asSequence() } }
            ?.firstOrNull { n -> RESTART_LABELS.any { n.text?.toString().equals(it, ignoreCase = true) } }
            ?.let { n -> generateSequence(n) { it.parent }.take(5).firstOrNull { it.isFocusable } }
        if (entry != null && entry.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            Toast.makeText(this, str(R.string.restart_press_ok), Toast.LENGTH_LONG).show()
            return
        }
        if (SystemClock.elapsedRealtime() < restartDeadline) handler.postDelayed(restartStep, 400)
    }
    private fun redirect() {
        val now = SystemClock.elapsedRealtime()
        if (now < pausedUntil) return
        // Mehrere Fenster-Events kurz nacheinander – nur einmal umleiten.
        if (now - lastRedirect < 1_500) return
        while (redirects.isNotEmpty() && now - redirects.first() > 10_000) redirects.removeFirst()
        if (redirects.size >= MAX_REDIRECTS) {
            redirects.clear()
            pausedUntil = now + 60_000
            hideCover()
            return
        }
        redirects.addLast(now)
        lastRedirect = now

        showCover()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun showCover(snapshot: android.graphics.Bitmap? = null) {
        if (cover != null) return
        val view = HomeCover(this, Appearance.isDark(this), snapshot)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { windowManager().addView(view, params) }.onSuccess {
            cover = view
            view.requestFocus()
            // Sicherheitsnetz, falls Lumen nicht hochkommt.
            handler.postDelayed(coverTimeout, COVER_TIMEOUT_MS)
        }
    }

    fun hideCover() {
        handler.removeCallbacks(coverTimeout)
        val view = cover ?: return
        cover = null
        view.animate().alpha(0f).setDuration(150).withEndAction {
            runCatching { windowManager().removeView(view) }
        }.start()
    }

    private fun windowManager() = getSystemService(WindowManager::class.java)

    override fun onInterrupt() = Unit

    companion object {
        private const val MAX_REDIRECTS = 6
        private const val COVER_TIMEOUT_MS = 8_000L
        private const val SETTINGS_POLL_MS = 800L
        private const val SETTINGS_SESSION_MAX_MS = 30 * 60_000L

        /** Einträge, die nur im Einstellungs-Tab der Amazon-Startseite stehen. */
        private val SETTINGS_MARKERS = listOf("Mein Fire TV", "Einstellungen durchsuchen", "My Fire TV")
        /** „Neu starten“ in Amazons Einstellungen – je nach Systemsprache. */
        private val RESTART_LABELS = listOf("Neu starten", "Restart")

        @Volatile
        var instance: HomeWatcherService? = null
            private set

        /** Fire OS schickt den Stick bei GLOBAL_ACTION_LOCK_SCREEN in den Ruhezustand. */
        fun sleep(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
