package de.tb.lumen

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Liest gezielt zwei Zeilen aus dem System-Log (Trick von „Launcher Manager“):
 *
 * - Home-Druck: Fire OS loggt bei jedem kurzen Home-Druck eine ContextImpl-Warnung mit
 *   „FireTVKeyPolicyManager…handleShortPressOnHome“ – ~0,3 s bevor die Amazon-Startseite
 *   gezeichnet wird (das Fenster-Event kommt erst danach).
 * - AirPlay („AirPlay Server“): „Sender connected/disconnected“ und „mirror running: 1“.
 *   Daraus entscheidet Lumen, wann die App nach vorne kommt und wann sie wieder zugeht.
 *
 * Gelesen werden nur diese Tags. Braucht READ_LOGS – einmalig per ADB:
 *   adb shell pm grant de.tb.lumen android.permission.READ_LOGS
 * (ohne die Berechtigung sieht Lumen nur eigene Logzeilen; dann passiert einfach nichts).
 */
class HomeKeyLog(
    private val onHome: () -> Unit,
    private val onAirPlayConnected: () -> Unit,
    private val onMirrorStarted: () -> Unit,
    private val onAudioOnly: () -> Unit,
    private val onAirPlayEnded: () -> Unit,
) {
    @Volatile
    private var process: Process? = null
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ run() }, "lumen-syslog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        thread = null
        process?.destroy()
        process = null
    }

    private fun run() {
        while (thread == Thread.currentThread()) {
            runCatching {
                // -T 1: nur neue Zeilen; -s: nur diese Tags.
                val p = ProcessBuilder(
                    "logcat", "-v", "brief", "-T", "1",
                    "-s", "ContextImpl:W", "AirPlayService:I", "AirPlayNative:I",
                ).redirectErrorStream(true).start()
                process = p
                BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                    for (line in lines) {
                        if (thread != Thread.currentThread()) break
                        when {
                            "handleShortPressOnHome" in line -> onHome()
                            "Sender connected" in line -> onAirPlayConnected()
                            "mirror running: 1" in line -> onMirrorStarted()
                            "screen=false" in line -> onAudioOnly()
                            "Sender disconnected" in line -> onAirPlayEnded()
                        }
                    }
                }
            }
            // logcat beendet (z. B. Puffer gelöscht) – kurz warten und neu verbinden.
            if (thread == Thread.currentThread()) Thread.sleep(2_000)
        }
    }
}
