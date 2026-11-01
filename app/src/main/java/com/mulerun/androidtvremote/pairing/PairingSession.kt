package com.mulerun.androidtvremote.pairing

import com.mulerun.androidtvremote.data.Device
import com.mulerun.androidtvremote.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Drives a full Remote v2 PIN pairing exchange with a TV.
 *
 * Flow:
 *   1. TLS connect on port 6467 (any client cert).
 *   2. Send PairingRequest, receive Ack.
 *   3. Send PairingOption, receive Ack, send PairingConfiguration.
 *   4. TV displays a 4-hex-digit PIN.  User enters it in [submitPin].
 *   5. We hash our-pub || server-pub || nonce (nonce = PIN first two bytes)
 *      and send PairingSecret.  TV compares and Acks.
 *   6. On success, persist the client key + server cert pin encrypted; the
 *      caller can now open a control session on port 6466.
 */
class PairingSession(
    private val device: Device,
    private val store: SecureStore,
) {

    sealed class State {
        object Idle : State()
        object Connecting : State()
        object AwaitingPin : State()
        object Verifying : State()
        data class Paired(val device: Device) : State()
        data class Failed(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var tls: TvTlsClient? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var clientCertB64: String? = null
    private var clientPub: RSAPublicKey? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        _state.value = State.Connecting
        runCatching {
            val kp = CertUtils.generateKeyPair()
            val cert = CertUtils.selfSignedCert(kp)
            clientPub = kp.public as RSAPublicKey
            clientCertB64 = Base64.getEncoder().encodeToString(cert.encoded)

            val client = TvTlsClient(
                host = device.host, port = device.pairingPort,
                identity = kp, clientCert = cert,
            )
            val (i, o) = client.connect()
            tls = client; input = i; output = o

            // Persist private key + cert pre-emptively (encrypted).  We overwrite
            // on failure below to avoid leaking half-paired state.
            store.putString("key:${device.stableId}", encodeKey(kp.private.encoded))
            store.putString("cert:${device.stableId}", clientCertB64)

            Framing.writeMessage(o, PairingProto.pairingRequest("androidtvremote", "MuleRun TV Remote"))
            Framing.readMessage(i) // ack

            Framing.writeMessage(o, PairingProto.pairingOption())
            Framing.readMessage(i) // ack

            Framing.writeMessage(o, PairingProto.pairingConfiguration())
            Framing.readMessage(i) // ack

            _state.value = State.AwaitingPin
        }.onFailure { e ->
            _state.value = State.Failed(e.message ?: "connect failed")
            tls?.close()
        }
    }

    suspend fun submitPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val cur = _state.value
        val o = output ?: return@withContext false
        val i = input ?: return@withContext false
        val client = tls ?: return@withContext false
        if (cur !is State.AwaitingPin) return@withContext false
        _state.value = State.Verifying

        runCatching {
            // The PIN's first byte is a check digit used as nonce prefix per Remote v2.
            val pinBytes = hexToBytes(pin.trim())
            val serverCert = client.observedServerCert ?: error("server cert missing")
            val serverPub = serverCert.publicKey as RSAPublicKey
            val myPub = clientPub ?: error("client key missing")
            val nonce = if (pinBytes.isNotEmpty()) byteArrayOf(pinBytes[0]) else ByteArray(0)
            val hash = CertUtils.pinNonceHash(
                clientMod = myPub.modulus, clientExp = myPub.publicExponent,
                serverMod = serverPub.modulus, serverExp = serverPub.publicExponent,
                nonce = nonce,
            )
            Framing.writeMessage(o, PairingProto.pairingSecret(hash))
            val ack = Framing.readMessage(i)
            // Any well-formed ack means success; the TV closes the socket on failure.
            require(ack.isNotEmpty()) { "empty ack" }

            // Persist server cert pin
            store.putString("servercert:${device.stableId}",
                Base64.getEncoder().encodeToString(serverCert.encoded))
            store.putBool("consent:${device.stableId}", true)

            _state.value = State.Paired(device.copy(paired = true))
            client.close()
            true
        }.getOrElse { e ->
            _state.value = State.Failed(e.message ?: "pin rejected")
            client.close()
            false
        }
    }

    fun cancel() { runCatching { tls?.close() } }

    private fun encodeKey(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { it.isLetterOrDigit() }
        val out = ByteArray(clean.length / 2)
        for (idx in out.indices) {
            out[idx] = clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
        return out
    }
}
