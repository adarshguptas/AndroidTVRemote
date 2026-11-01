package com.mulerun.androidtvremote.control

import com.mulerun.androidtvremote.data.Device
import com.mulerun.androidtvremote.data.SecureStore
import com.mulerun.androidtvremote.pairing.CertUtils
import com.mulerun.androidtvremote.pairing.Framing
import com.mulerun.androidtvremote.pairing.TvTlsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Persistent control session over the TV's port 6466 channel. Requires the
 * device to be paired first (client cert + pinned server cert in SecureStore).
 *
 * Sending is serialised through a mutex; the reader coroutine drains the
 * socket to keep the TV happy and detect disconnects.
 */
class ControlSession(
    private val device: Device,
    private val store: SecureStore,
) {

    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        data class Connected(val device: Device) : State()
        data class Error(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null
    private var pingerJob: Job? = null
    private var tls: TvTlsClient? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    fun consentGranted(): Boolean = store.getBool("consent:${device.stableId}", false)

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (!consentGranted()) {
            _state.value = State.Error("device not paired / consent missing")
            return@withContext
        }
        _state.value = State.Connecting
        runCatching {
            val (kp, cert) = loadIdentity() ?: error("identity missing — re-pair required")
            val pinned = loadPinnedCert()

            val client = TvTlsClient(
                host = device.host, port = device.port,
                identity = kp, clientCert = cert, pinnedServerCert = pinned,
            )
            val (i, o) = client.connect()
            tls = client; input = i; output = o

            Framing.writeMessage(o, ControlProto.configure("MuleRun TV Remote"))
            Framing.writeMessage(o, ControlProto.setActive(true))

            _state.value = State.Connected(device)
            startReader(i)
            startPinger()
        }.onFailure { e ->
            _state.value = State.Error(e.message ?: "connect failed")
            close()
        }
    }

    private fun startReader(i: InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                while (isActive) {
                    // Drain frames; log-only for now.
                    Framing.readMessage(i)
                }
            } catch (t: Throwable) {
                _state.value = State.Error(t.message ?: "disconnected")
                close()
            }
        }
    }

    private fun startPinger() {
        pingerJob?.cancel()
        pingerJob = scope.launch {
            var seq = 0
            while (isActive) {
                delay(15_000)
                runCatching { sendRaw(ControlProto.ping(++seq)) }
            }
        }
    }

    suspend fun sendKey(keyCode: Int, longPress: Boolean = false) =
        sendRaw(ControlProto.keyInject(keyCode, longPress))

    suspend fun setVolumePercent(target: Int) {
        // We can't set absolute volume via Remote v2, so approximate by
        // pressing VOL_DOWN to zero then VOL_UP up. TVs generally step in 5%.
        val clamped = target.coerceIn(0, 100)
        repeat(24) { sendKey(KeyCodes.VOL_DOWN) }
        val steps = (clamped / 5).coerceIn(0, 20)
        repeat(steps) { sendKey(KeyCodes.VOL_UP) }
    }

    suspend fun launchAppUri(uri: String) = sendRaw(ControlProto.startApp(uri))

    suspend fun sendText(text: String) {
        // The v2 protocol has no bulk text field; each char is dispatched as
        // a KEYCODE.  We map ASCII printable chars to A-Z / 0-9 keycodes.
        for (ch in text) {
            val code = ch.uppercaseChar().code
            when (code) {
                in 65..90 -> sendKey(29 + (code - 65))          // A..Z -> 29..54
                in 48..57 -> sendKey(7 + (code - 48))           // 0..9 -> 7..16
                32 -> sendKey(62)                                // SPACE
                else -> { /* skip */ }
            }
        }
    }

    private suspend fun sendRaw(payload: ByteArray) {
        val o = output ?: return
        writeMutex.withLock {
            runCatching { Framing.writeMessage(o, payload) }
                .onFailure { _state.value = State.Error(it.message ?: "write failed"); close() }
        }
    }

    fun close() {
        readerJob?.cancel(); pingerJob?.cancel()
        runCatching { tls?.close() }
        input = null; output = null; tls = null
        if (_state.value is State.Connected) _state.value = State.Disconnected
    }

    private fun loadIdentity(): Pair<KeyPair, X509Certificate>? {
        val keyB64 = store.getString("key:${device.stableId}") ?: return null
        val certB64 = store.getString("cert:${device.stableId}") ?: return null
        val keyBytes = Base64.getDecoder().decode(keyB64)
        val certBytes = Base64.getDecoder().decode(certB64)
        val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(certBytes.inputStream()) as X509Certificate
        val pub = cert.publicKey
        return KeyPair(pub, priv) to cert
    }

    private fun loadPinnedCert(): X509Certificate? {
        val b64 = store.getString("servercert:${device.stableId}") ?: return null
        val bytes = Base64.getDecoder().decode(b64)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(bytes.inputStream()) as X509Certificate
    }
}
