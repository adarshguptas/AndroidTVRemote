package com.mulerun.androidtvremote.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bluetooth discovery + secure PIN pairing helper.
 *
 * Android TVs advertise themselves over Classic Bluetooth as devices of
 * class AUDIO_VIDEO / TV. We surface every discovered BT device (filtered
 * to TV-ish major class) and delegate the actual pairing to Android's own
 * secure OS-level pairing flow — which shows the standard PIN/passkey
 * confirmation dialog on both the phone and the TV. This gives us the
 * "Bluetooth security code pairing" experience without ever handling the
 * PIN in-process, so it remains cryptographically secure.
 */
class BluetoothTv(private val context: Context) {

    data class BtDev(val name: String, val address: String, val bonded: Boolean)

    fun supported(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter != null
    }

    fun enabled(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled == true
    }

    fun hasPermissions(): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BtDev> {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices
                .filter { looksLikeTv(it) }
                .map { BtDev(it.name ?: it.address, it.address, bonded = true) }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun discover(): Flow<List<BtDev>> = callbackFlow {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: run { close(); return@callbackFlow }
        val list = LinkedHashMap<String, BtDev>()

        // seed with already-bonded TVs
        bondedDevices().forEach { list[it.address] = it }
        trySend(list.values.toList())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (dev != null && looksLikeTv(dev)) {
                            val name = runCatching { dev.name }.getOrNull() ?: dev.address
                            list[dev.address] = BtDev(name, dev.address, dev.bondState == BluetoothDevice.BOND_BONDED)
                            trySend(list.values.toList())
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        trySend(bondedDevices().plus(list.values.filter { !it.bonded }).distinctBy { it.address })
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, flag)
        } else {
            context.registerReceiver(receiver, filter)
        }

        runCatching {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        }

        awaitClose {
            runCatching { adapter.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /**
     * Initiates the OS-level bond (pairing) request. The system will show the
     * secure PIN/passkey confirmation dialog on both the phone and the TV.
     * Returns true if the request was accepted (result comes asynchronously
     * via ACTION_BOND_STATE_CHANGED).
     */
    @SuppressLint("MissingPermission")
    fun pair(address: String): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return false
        val dev = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        if (dev.bondState == BluetoothDevice.BOND_BONDED) return true
        return runCatching { dev.createBond() }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun looksLikeTv(dev: BluetoothDevice): Boolean {
        val major = dev.bluetoothClass?.majorDeviceClass ?: return true
        // AUDIO_VIDEO major class is used by TVs, soundbars, streaming boxes.
        return major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO || major == 0
    }

    fun enableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
}
