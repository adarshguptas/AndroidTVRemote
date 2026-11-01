package com.mulerun.androidtvremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.mulerun.androidtvremote.data.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

/**
 * Discovers Android TVs on the local network via mDNS.
 *
 * Standard Android TV Remote v2 advertises the service type
 *   _androidtvremote2._tcp.
 * We also observe _googlecast._tcp. (Google Cast) to catch older devices that
 * expose only the Cast service; those can be paired via Cast SDK if desired.
 */
class TvDiscovery(private val context: Context) {

    fun discover(): Flow<List<Device>> = callbackFlow {
        val multicastLock = (context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("androidtv-remote-mdns").apply {
                setReferenceCounted(true); acquire()
            }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val found = LinkedHashMap<String, Device>()

        var jmdns: JmDNS? = null
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns?.requestServiceInfo(event.type, event.name, 1000)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val key = event.name
                if (found.remove(key) != null) trySend(found.values.toList())
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val host = info.inet4Addresses.firstOrNull()?.hostAddress
                    ?: info.inetAddresses.firstOrNull()?.hostAddress ?: return
                val model = info.getPropertyString("md") ?: info.getPropertyString("fn")
                val dev = Device(
                    name = info.name.ifBlank { model ?: host },
                    host = host,
                    port = if (info.port > 0) info.port else 6466,
                    pairingPort = 6467,
                    model = model,
                )
                found[event.name] = dev
                trySend(found.values.toList())
            }
        }

        scope.launch {
            runCatching {
                val addr = pickLocalAddress()
                jmdns = JmDNS.create(addr)
                jmdns?.addServiceListener("_androidtvremote2._tcp.local.", listener)
                jmdns?.addServiceListener("_googlecast._tcp.local.", listener)
            }
        }

        awaitClose {
            runCatching { jmdns?.close() }
            runCatching { if (multicastLock.isHeld) multicastLock.release() }
        }
    }

    private fun pickLocalAddress(): InetAddress {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo?.ipAddress ?: 0
        return if (ip != 0) {
            val bytes = ByteArray(4)
            bytes[0] = (ip and 0xff).toByte()
            bytes[1] = ((ip shr 8) and 0xff).toByte()
            bytes[2] = ((ip shr 16) and 0xff).toByte()
            bytes[3] = ((ip shr 24) and 0xff).toByte()
            InetAddress.getByAddress(bytes)
        } else InetAddress.getLocalHost()
    }
}
