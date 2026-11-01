package com.mulerun.androidtvremote.pairing

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Utilities for generating a client X.509 identity for TLS mutual auth
 * with an Android TV, and for computing the hash comparison used during
 * the Remote v2 PIN pairing handshake.
 *
 * Note: The Android TV Remote v2 protocol uses a nonce-check where the app
 * proves knowledge of the 4-digit PIN by hashing:
 *   SHA-256( client_pub_modulus || client_pub_exp || server_pub_modulus || server_pub_exp || nonce )
 * and comparing the first 4 hex bytes with the PIN. We expose the primitive here.
 */
object CertUtils {

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        return kpg.generateKeyPair()
    }

    /**
     * Builds a self-signed X.509 certificate for the given key pair without
     * pulling in BouncyCastle. Uses a minimal DER encoder for CN=TVRemote.
     */
    fun selfSignedCert(kp: KeyPair): X509Certificate {
        // Minimal: rely on platform provider by constructing via KeyStore.
        // We build a synthetic cert wrapper using the JCA CertificateFactory
        // fed by a DER blob produced with a tiny in-house encoder.
        val der = MinimalX509.build(kp, "CN=TVRemote")
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(der.inputStream()) as X509Certificate
    }

    /** Compute the PIN nonce hash per Remote v2. */
    fun pinNonceHash(
        clientMod: BigInteger, clientExp: BigInteger,
        serverMod: BigInteger, serverExp: BigInteger,
        nonce: ByteArray,
    ): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(clientMod))
        md.update(unsigned(clientExp))
        md.update(unsigned(serverMod))
        md.update(unsigned(serverExp))
        md.update(nonce)
        return md.digest()
    }

    private fun unsigned(b: BigInteger): ByteArray {
        val a = b.toByteArray()
        return if (a[0] == 0.toByte()) a.copyOfRange(1, a.size) else a
    }
}

/**
 * Extremely small self-signed cert builder. This intentionally avoids
 * BouncyCastle so the release APK stays small. It emits a v1 X.509 cert
 * good for TLS client auth on private networks.
 */
private object MinimalX509 {
    fun build(kp: KeyPair, subject: String): ByteArray {
        // For production use, integrate a proper ASN.1 library. For this
        // reference implementation we generate a certificate lazily via
        // the platform's own KeyStore self-signing helper.
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null)
        val fake = fakeCert(kp, subject)
        return fake.encoded
    }

    // Placeholder: emits a hand-rolled minimally valid v1 cert. In real
    // deployment this is generated once at pairing time and stored in
    // AndroidKeyStore. For brevity, we defer to a helper cert file.
    private fun fakeCert(kp: KeyPair, subject: String): X509Certificate {
        // Build via SelfSignedCertHelper (below) to keep code readable.
        return SelfSignedCertHelper.generate(kp, subject, notAfterYears = 20)
    }
}

/**
 * Self-signed certificate generator using ASN.1 DER hand-encoding.
 * Enough for TLS client auth handshake — Android TV accepts any client cert
 * during pairing and pins it thereafter.
 */
internal object SelfSignedCertHelper {
    fun generate(kp: KeyPair, subjectCN: String, notAfterYears: Int): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + notAfterYears * 365L * 24 * 3600 * 1000)
        val serial = BigInteger(64, SecureRandom())

        val pub = kp.public as RSAPublicKey
        val tbs = Asn1.tbsCertificate(serial, subjectCN, notBefore, notAfter, pub.encoded)
        val sig = Asn1.signSha256WithRsa(tbs, kp.private)
        val cert = Asn1.certificate(tbs, sig)
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(cert.inputStream()) as X509Certificate
    }
}
