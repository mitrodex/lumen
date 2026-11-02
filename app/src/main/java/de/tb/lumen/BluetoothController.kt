package de.tb.lumen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class BtDevice(val address: String, val name: String, val audio: Boolean, val bonded: Boolean, val connected: Boolean)

/**
 * Bluetooth-Kopfhörer suchen, koppeln, verbinden. Bewusst KEIN Ausschalten von Bluetooth –
 * die Fire-TV-Fernbedienung hängt daran. Angezeigt werden nur Audio-Geräte, damit man nicht
 * versehentlich die Fernbedienung trennt.
 */
@SuppressLint("MissingPermission")
class BluetoothController(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var a2dp: BluetoothA2dp? = null
    private val found = LinkedHashMap<String, BluetoothDevice>()

    var devices by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND ->
                    i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.let { found[it.address] = it }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null && d.bondState == BluetoothDevice.BOND_BONDED) connect(d.address)
                }
            }
            update()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            a2dp = proxy as? BluetoothA2dp
            update()
        }

        override fun onServiceDisconnected(profile: Int) {
            a2dp = null
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        })
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        update()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { adapter?.cancelDiscovery() }
        a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
    }

    fun scan() {
        val a = adapter ?: return
        found.clear()
        scanning = runCatching { a.cancelDiscovery(); a.startDiscovery() }.getOrDefault(false)
        status = str(if (scanning) R.string.bt_searching else R.string.bt_search_failed)
        update()
    }

    private fun isAudio(d: BluetoothDevice) =
        d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO

    private fun update() {
        val a = adapter ?: return
        val connected = a2dp?.connectedDevices.orEmpty().map { it.address }.toSet()
        val bonded = a.bondedDevices.orEmpty()
        val all = LinkedHashMap<String, BluetoothDevice>()
        bonded.forEach { all[it.address] = it }
        found.values.forEach { all.putIfAbsent(it.address, it) }
        devices = all.values
            .filter { isAudio(it) && !it.name.isNullOrBlank() }
            .map { BtDevice(it.address, it.name, true, it.bondState == BluetoothDevice.BOND_BONDED, it.address in connected) }
            .sortedWith(compareByDescending<BtDevice> { it.connected }.thenByDescending { it.bonded }.thenBy { it.name })
    }

    /** Klick auf ein Gerät: koppeln, verbinden oder trennen – je nach Zustand. */
    fun activate(dev: BtDevice) {
        when {
            dev.connected -> disconnect(dev.address)
            dev.bonded -> connect(dev.address)
            else -> {
                runCatching { adapter?.cancelDiscovery() }
                val ok = adapter?.getRemoteDevice(dev.address)?.createBond() == true
                status = if (ok) str(R.string.bt_pairing, dev.name) else str(R.string.bt_pair_failed)
            }
        }
    }

    // connect/disconnect sind in Android 9 versteckte A2DP-APIs – per Reflection.
    private fun connect(address: String) = callA2dp("connect", address, R.string.bt_connecting)
    private fun disconnect(address: String) = callA2dp("disconnect", address, R.string.bt_disconnecting)

    private fun callA2dp(method: String, address: String, @androidx.annotation.StringRes progress: Int) {
        val proxy = a2dp
        val device = adapter?.getRemoteDevice(address)
        if (proxy == null || device == null) {
            status = str(R.string.bt_profile_not_ready)
            return
        }
        val ok = runCatching {
            proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as Boolean
        }.getOrDefault(false)
        status = if (ok) str(progress, device.name.orEmpty()) else str(R.string.bt_failed)
    }
}
