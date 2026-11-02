package de.tb.lumen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Hält den AirPlay-Empfänger („AirPlay Server“, io.github.jqssun.airplay) dauerhaft bereit,
 * damit das iPhone den Fire TV jederzeit zum Bildschirm-Synchronisieren findet.
 *
 * Die App startet ihren Hintergrunddienst selbst, wenn ihr BootReceiver ein
 * QUICKBOOT_POWERON bekommt – das darf Lumen (anders als BOOT_COMPLETED) senden, und es
 * öffnet nichts sichtbar. Ob der Dienst läuft, erkennt Lumen daran, dass Port 7000 antwortet.
 * Bei einer Verbindung öffnet sich die App dank ihrer Einstellung „launch_on_connect“ selbst.
 */
object AirPlayKeeper {
    const val PACKAGE = "io.github.jqssun.airplay"
    private const val PORT = 7000

    /** Blockiert kurz (Netzwerk) – nur aus einem Hintergrund-Thread aufrufen. */
    fun ensureRunning(context: Context) {
        if (!isInstalled(context) || isListening()) return
        context.sendBroadcast(
            Intent("android.intent.action.QUICKBOOT_POWERON")
                .setComponent(ComponentName(PACKAGE, "$PACKAGE.BootReceiver"))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        )
    }

    private fun isInstalled(context: Context) =
        runCatching { context.packageManager.getApplicationInfo(PACKAGE, 0).enabled }.getOrDefault(false)

    private fun isListening(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 500) }
        true
    }.getOrDefault(false)
}
