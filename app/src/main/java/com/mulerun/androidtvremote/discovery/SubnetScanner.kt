package com.mulerun.androidtvremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.mulerun.androidtvremote.data.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fallback discovery: some Wi-Fi routers block mDNS multicast. When that
 * happens the app can't see the TV over Bonjour. This scanner walks the
 * local /24 and TCP-probes port 6466 (Android TV Remote v2 control) — any
 * host that accepts the connection is reported as a candidate device.
 */
class SubnetScanner(private val context: Context) {

    fun scan(): Flow<List<Device>> = callbackFlow {
        val found = LinkedHashMap<String, Device>()
        val base = detectLocalPrefix() ?: run { close(); return@callbackFlow }
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val jobs = (1..254).map { i ->
                async {
                    val host = "$base.$i"
                    if (tcpOpen(host, 6466, 250)) {
                        val d = Device(name = "Android TV @ $host", host = host)
                        synchronized(found) {
                            found[host] = d
                            trySend(found.values.toList())
                        }
                    }
                }
            }
            jobs.awaitAll()
            close()
        }
        awaitClose { }
    }

    private fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    private fun detectLocalPrefix(): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        val a = ip and 0xff
        val b = (ip shr 8) and 0xff
        val c = (ip shr 16) and 0xff
        return "$a.$b.$c"
    }
}
