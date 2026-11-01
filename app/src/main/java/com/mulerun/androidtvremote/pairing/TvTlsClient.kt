package com.mulerun.androidtvremote.pairing

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Establishes a TLS socket to an Android TV using a client X.509 identity.
 *
 * During pairing (port 6467) the TV will accept any client cert and we later
 * pin the server's cert. During control (port 6466) we present the same
 * cert so the TV can recognise us as a previously-paired remote.
 *
 * Certificate pinning is enforced at connect() time when [pinnedServerCert]
 * is provided; otherwise the trust manager records the observed cert.
 */
class TvTlsClient(
    private val host: String,
    private val port: Int,
    private val identity: KeyPair,
    private val clientCert: X509Certificate,
    private val pinnedServerCert: X509Certificate? = null,
) {
    var observedServerCert: X509Certificate? = null; private set
    private var socket: SSLSocket? = null

    fun connect(timeoutMs: Int = 10_000): Pair<InputStream, OutputStream> {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setKeyEntry("me", identity.private, CharArray(0), arrayOf(clientCert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))

        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw javax.net.ssl.SSLException("empty chain")
                observedServerCert = leaf
                pinnedServerCert?.let {
                    if (!leaf.encoded.contentEquals(it.encoded)) {
                        throw javax.net.ssl.SSLPeerUnverifiedException("Server cert changed — refusing connection")
                    }
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLSv1.2")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(tm), java.security.SecureRandom())

        val factory: SSLSocketFactory = ctx.socketFactory
        val plain = Socket()
        plain.connect(InetSocketAddress(host, port), timeoutMs)
        val s = factory.createSocket(plain, host, port, true) as SSLSocket
        s.soTimeout = 15_000
        s.enabledProtocols = arrayOf("TLSv1.2")
        s.startHandshake()
        socket = s
        return s.inputStream to s.outputStream
    }

    fun close() { runCatching { socket?.close() } }
}
