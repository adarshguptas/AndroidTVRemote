package com.mulerun.androidtvremote.pairing

import java.io.ByteArrayOutputStream

/**
 * Minimal hand-rolled protobuf builder for the messages needed during the
 * Android TV Remote v2 pairing handshake.
 *
 * The v2 pairing protobuf schema (as documented publicly by the community —
 * see google/tv-remote-android-code) uses these top-level messages:
 *   1: PairingRequest        { service_name, client_name }
 *   2: PairingRequestAck     { server_name }
 *   3: PairingOption         { output_encoding, input_encoding, preferred_role }
 *   4: PairingConfiguration  { encoding, client_role }
 *   5: PairingConfigurationAck
 *   6: PairingSecret         { secret }        // hash prefix bytes derived from PIN
 *   7: PairingSecretAck
 *
 * Every message is wrapped in an outer PairingMessage that carries a
 * status/protocol_version envelope. The exact field numbers used below match
 * that outer envelope; if your target TV firmware differs, swap the tag ints
 * in one place and everything else stays the same.
 */
object PairingProto {
    // Outer envelope fields
    const val TAG_PROTO_VER = 1
    const val TAG_STATUS = 2

    fun envelope(protocolVer: Int, status: Int, innerTag: Int, inner: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        Proto.writeVarintField(body, TAG_PROTO_VER, protocolVer.toLong())
        Proto.writeVarintField(body, TAG_STATUS, status.toLong())
        Proto.writeLengthDelimited(body, innerTag, inner)
        return body.toByteArray()
    }

    fun pairingRequest(serviceName: String, clientName: String): ByteArray {
        val b = ByteArrayOutputStream()
        Proto.writeString(b, 1, serviceName)
        Proto.writeString(b, 2, clientName)
        return envelope(protocolVer = 2, status = 200, innerTag = 10, inner = b.toByteArray())
    }

    fun pairingOption(): ByteArray {
        // Ask for 4-hex encoding (matches PIN semantic)
        val enc = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, 3) // ENCODING_HEXADECIMAL
            Proto.writeVarintField(this, 2, 4) // symbol length = 4
        }
        val b = ByteArrayOutputStream()
        Proto.writeLengthDelimited(b, 1, enc.toByteArray())
        Proto.writeVarintField(b, 3, 1) // client role
        return envelope(2, 200, 20, b.toByteArray())
    }

    fun pairingConfiguration(): ByteArray {
        val enc = ByteArrayOutputStream().apply {
            Proto.writeVarintField(this, 1, 3)
            Proto.writeVarintField(this, 2, 4)
        }
        val b = ByteArrayOutputStream()
        Proto.writeLengthDelimited(b, 1, enc.toByteArray())
        Proto.writeVarintField(b, 2, 1)
        return envelope(2, 200, 30, b.toByteArray())
    }

    fun pairingSecret(secret: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        Proto.writeLengthDelimited(b, 1, secret)
        return envelope(2, 200, 40, b.toByteArray())
    }
}

/**
 * Tiny hand-rolled protobuf wire-format helpers.  Enough to encode the
 * pairing envelope + a handful of key events for control. We deliberately
 * avoid pulling in a 500 KB protobuf-lite runtime.
 */
object Proto {
    private const val WIRE_VARINT = 0
    private const val WIRE_LEN = 2

    fun writeVarintField(out: ByteArrayOutputStream, tag: Int, value: Long) {
        writeVarint(out, ((tag shl 3) or WIRE_VARINT).toLong()); writeVarint(out, value)
    }

    fun writeLengthDelimited(out: ByteArrayOutputStream, tag: Int, data: ByteArray) {
        writeVarint(out, ((tag shl 3) or WIRE_LEN).toLong())
        writeVarint(out, data.size.toLong()); out.write(data)
    }

    fun writeString(out: ByteArrayOutputStream, tag: Int, s: String) =
        writeLengthDelimited(out, tag, s.toByteArray(Charsets.UTF_8))

    private fun writeVarint(out: ByteArrayOutputStream, valueIn: Long) {
        var v = valueIn
        while ((v and 0x7fL.inv()) != 0L) {
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7f).toInt())
    }
}
