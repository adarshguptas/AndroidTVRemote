package com.mulerun.androidtvremote.control

import com.mulerun.androidtvremote.pairing.Proto
import java.io.ByteArrayOutputStream

/**
 * Encoders for the (subset of) Android TV Remote v2 control-channel messages
 * we send.  The v2 control protobuf uses these top-level messages:
 *
 *   1  remote_configure     — handshake with device name + version
 *   2  remote_set_active    — announce active
 *   7  remote_key_inject    — inject a KeyEvent (keycode + action)
 *   8  remote_start_app     — launch an app by intent/URI
 *   90 remote_ping / pong   — heartbeat
 *
 * We only encode the request side; responses are ignored except for closing
 * the socket on error.
 */
object ControlProto {

    fun configure(clientName: String): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, 622)          // version code
            Proto.writeString(this, 2, clientName)         // client friendly name
            Proto.writeString(this, 3, "MuleRun-Remote")   // model
            Proto.writeString(this, 4, "1.0")              // app version
        }.toByteArray()
        return envelope(1, inner)
    }

    fun setActive(active: Boolean): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, if (active) 622 else 0)
        }.toByteArray()
        return envelope(2, inner)
    }

    fun keyInject(keyCode: Int, longPress: Boolean = false): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, keyCode.toLong())
            // 1 = SHORT, 2 = START_LONG, 3 = END_LONG
            Proto.writeVarintField(this, 2, if (longPress) 3 else 1)
        }.toByteArray()
        return envelope(7, inner)
    }

    fun startApp(uri: String): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeString(this, 1, uri) // e.g. "https://www.youtube.com" or "market://…"
        }.toByteArray()
        return envelope(8, inner)
    }

    fun voiceBegin(): ByteArray = envelope(11, ByteArray(0))
    fun voiceEnd(): ByteArray = envelope(12, ByteArray(0))
    fun voicePayload(pcm: ByteArray): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeLengthDelimited(this, 1, pcm)
        }.toByteArray()
        return envelope(13, inner)
    }

    fun ping(seq: Int): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, seq.toLong())
        }.toByteArray()
        return envelope(90, inner)
    }

    private fun envelope(tag: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        Proto.writeLengthDelimited(out, tag, body)
        return out.toByteArray()
    }
}

/**
 * Known Android TV app URIs for common apps. Users can add more via
 * settings later.
 */
object AppShortcuts {
    val byName: Map<String, String> = linkedMapOf(
        "youtube" to "https://www.youtube.com/tv",
        "netflix" to "https://www.netflix.com/title.html",
        "prime video" to "https://app.primevideo.com",
        "disney+" to "https://www.disneyplus.com",
        "disney plus" to "https://www.disneyplus.com",
        "spotify" to "spotify://",
        "plex" to "plex://",
        "hbo max" to "https://play.max.com",
        "max" to "https://play.max.com",
        "twitch" to "https://www.twitch.tv",
    )
    fun resolve(query: String): String? {
        val q = query.trim().lowercase()
        byName[q]?.let { return it }
        return byName.entries.firstOrNull { q.contains(it.key) }?.value
    }
}
