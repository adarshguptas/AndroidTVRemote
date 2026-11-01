package com.mulerun.androidtvremote.data

import java.net.InetAddress

/** A discovered or paired Android TV on the LAN. */
data class Device(
    val name: String,
    val host: String,
    val port: Int = 6466,
    val pairingPort: Int = 6467,
    val model: String? = null,
    val paired: Boolean = false,
) {
    val stableId: String get() = "$host:$port"
}
