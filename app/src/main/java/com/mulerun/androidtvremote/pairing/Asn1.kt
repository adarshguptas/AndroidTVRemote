package com.mulerun.androidtvremote.pairing

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.PrivateKey
import java.security.Signature
import java.util.Date

/**
 * Small, dependency-free DER/ASN.1 encoder — just enough to emit a v1
 * self-signed X.509 certificate for RSA-2048 keys used during Android TV
 * Remote v2 pairing.
 */
internal object Asn1 {
    private const val SEQ = 0x30
    private const val SET = 0x31
    private const val OID = 0x06
    private const val INT = 0x02
    private const val BIT = 0x03
    private const val NUL = 0x05
    private const val UTF8 = 0x0C
    private const val PRINTSTR = 0x13
    private const val UTC = 0x17

    // OIDs
    private val oidSha256Rsa = fromDotted("1.2.840.113549.1.1.11")
    private val oidRsa = fromDotted("1.2.840.113549.1.1.1")
    private val oidCn = fromDotted("2.5.4.3")

    fun tbsCertificate(
        serial: BigInteger, cn: String, notBefore: Date, notAfter: Date,
        subjectPubKeyInfoDer: ByteArray,
    ): ByteArray {
        val issuerAndSubject = name(cn)
        val validity = seq(utcTime(notBefore) + utcTime(notAfter))
        val body =
            integer(serial) +
                sigAlg() +
                issuerAndSubject +
                validity +
                issuerAndSubject +
                subjectPubKeyInfoDer
        return seq(body)
    }

    fun signSha256WithRsa(tbs: ByteArray, key: PrivateKey): ByteArray {
        val s = Signature.getInstance("SHA256withRSA"); s.initSign(key); s.update(tbs)
        return s.sign()
    }

    fun certificate(tbs: ByteArray, sig: ByteArray): ByteArray {
        return seq(tbs + sigAlg() + bitString(sig))
    }

    private fun sigAlg(): ByteArray = seq(oid(oidSha256Rsa) + nullValue())

    private fun name(cn: String): ByteArray {
        val rdn = set(seq(oid(oidCn) + utf8(cn)))
        return seq(rdn)
    }

    // --- Primitive encoders ---
    private fun seq(body: ByteArray) = tlv(SEQ, body)
    private fun set(body: ByteArray) = tlv(SET, body)
    private fun oid(bytes: ByteArray) = tlv(OID, bytes)
    private fun integer(b: BigInteger) = tlv(INT, b.toByteArray())
    private fun bitString(b: ByteArray) = tlv(BIT, byteArrayOf(0) + b)
    private fun utf8(s: String) = tlv(UTF8, s.toByteArray(Charsets.UTF_8))
    private fun utcTime(d: Date): ByteArray {
        val fmt = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return tlv(UTC, fmt.format(d).toByteArray(Charsets.US_ASCII))
    }
    private fun nullValue() = tlv(NUL, ByteArray(0))

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            else -> {
                val bytes = mutableListOf<Byte>()
                var x = len
                while (x > 0) { bytes.add(0, (x and 0xff).toByte()); x = x ushr 8 }
                out.write(0x80 or bytes.size)
                bytes.forEach { out.write(it.toInt() and 0xff) }
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun fromDotted(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayDeque<Int>()
            stack.addFirst((v and 0x7f).toInt())
            v = v ushr 7
            while (v > 0) { stack.addFirst(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
            stack.forEach { out.write(it) }
        }
        return out.toByteArray()
    }
}
