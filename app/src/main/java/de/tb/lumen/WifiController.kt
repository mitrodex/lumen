package de.tb.lumen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class WifiNetwork(val ssid: String, val secured: Boolean, val level: Int, val connected: Boolean, val saved: Boolean)

/**
 * WLAN suchen und verbinden (Android 9: WifiManager-APIs sind für Apps noch erlaubt).
 * Bewusst KEIN Ausschalten – sonst wären Netzwerk und ADB weg.
 */
class WifiController(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    var networks by mutableStateOf<List<WifiNetwork>>(emptyList())
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = update()
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        })
        update()
        scan()
    }

    fun stop() = runCatching { context.unregisterReceiver(receiver) }

    fun scan() {
        runCatching { @Suppress("DEPRECATION") wifi?.startScan() }
    }

    @Suppress("DEPRECATION")
    private fun update() {
        val wm = wifi ?: return
        val current = wm.connectionInfo?.ssid?.trim('"')
        val saved = runCatching { wm.configuredNetworks.orEmpty().mapNotNull { it.SSID?.trim('"') }.toSet() }.getOrDefault(emptySet())
        networks = wm.scanResults.orEmpty()
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxBy { it.level }
                WifiNetwork(
                    ssid = ssid,
                    secured = isSecured(best),
                    level = WifiManager.calculateSignalLevel(best.level, 4),
                    connected = ssid == current,
                    saved = ssid in saved,
                )
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    private fun isSecured(r: ScanResult) = listOf("WEP", "PSK", "EAP", "SAE").any { it in r.capabilities }

    /** Verbindet; [password] null = offenes oder bereits gespeichertes Netz. */
    @Suppress("DEPRECATION")
    fun connect(net: WifiNetwork, password: String?): Boolean {
        val wm = wifi ?: return false
        val quoted = "\"${net.ssid}\""
        val existing = wm.configuredNetworks.orEmpty().firstOrNull { it.SSID == quoted }
        val id = if (existing != null && password == null) {
            existing.networkId
        } else {
            val cfg = WifiConfiguration().apply {
                SSID = quoted
                if (net.secured && password != null) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }
            wm.addNetwork(cfg)
        }
        if (id < 0) {
            status = "Netz konnte nicht angelegt werden"
            return false
        }
        val ok = wm.enableNetwork(id, true) && wm.reconnect()
        status = if (ok) "Verbinde mit ${net.ssid} …" else "Verbinden fehlgeschlagen"
        return ok
    }
}
