package com.mulerun.androidtvremote.pairing

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * The Android TV Remote v2 protocol frames each protobuf message with a
 * single unsigned length byte for messages < 128 bytes, otherwise a big-endian
 * varint. This implementation mirrors that framing so real protobuf payloads
 * (generated separately) can be sent/received on the same socket.
 */
object Framing {

    fun writeMessage(out: OutputStream, payload: ByteArray) {
        val len = payload.size
        require(len in 0..0x7fffffff)
        val header = when {
            len < 0x80 -> byteArrayOf(len.toByte())
            else -> {
                val b3 = ((len ushr 24) and 0xff).toByte()
                val b2 = ((len ushr 16) and 0xff).toByte()
                val b1 = ((len ushr 8) and 0xff).toByte()
                val b0 = (len and 0xff).toByte()
                byteArrayOf(b3, b2, b1, b0)
            }
        }
        out.write(header); out.write(payload); out.flush()
    }

    fun readMessage(input: InputStream): ByteArray {
        val first = input.read()
        if (first < 0) throw EOFException("stream closed")
        val len = if (first < 0x80) first else {
            val b2 = must(input); val b1 = must(input); val b0 = must(input)
            ((first and 0x7f) shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }
        val buf = ByteArray(len); var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n < 0) throw EOFException("stream closed")
            read += n
        }
        return buf
    }

    private fun must(input: InputStream): Int {
        val v = input.read(); if (v < 0) throw EOFException("stream closed"); return v
    }
}
