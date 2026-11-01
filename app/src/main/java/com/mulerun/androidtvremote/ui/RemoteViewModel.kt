package com.mulerun.androidtvremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mulerun.androidtvremote.RemoteApp
import com.mulerun.androidtvremote.control.AppShortcuts
import com.mulerun.androidtvremote.control.ControlSession
import com.mulerun.androidtvremote.control.KeyCodes
import com.mulerun.androidtvremote.data.Device
import com.mulerun.androidtvremote.data.ThemeMode
import com.mulerun.androidtvremote.data.ThemePref
import com.mulerun.androidtvremote.discovery.BluetoothTv
import com.mulerun.androidtvremote.discovery.SubnetScanner
import com.mulerun.androidtvremote.discovery.TvDiscovery
import com.mulerun.androidtvremote.pairing.PairingSession
import com.mulerun.androidtvremote.voice.CommandExecutor
import com.mulerun.androidtvremote.voice.NlParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val store = RemoteApp.instance.secureStore
    private val discovery = TvDiscovery(ctx)
    private val subnetScanner = SubnetScanner(ctx)
    val bluetooth = BluetoothTv(ctx)
    private val themePref = ThemePref(ctx)

    val theme: StateFlow<ThemeMode> = themePref.mode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _status = MutableStateFlow<String>("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastVoice = MutableStateFlow("")
    val lastVoice: StateFlow<String> = _lastVoice.asStateFlow()

    private val _btDevices = MutableStateFlow<List<BluetoothTv.BtDev>>(emptyList())
    val btDevices: StateFlow<List<BluetoothTv.BtDev>> = _btDevices.asStateFlow()

    private var discoveryJob: Job? = null
    private var subnetJob: Job? = null
    private var btJob: Job? = null
    private var control: ControlSession? = null
    private var pairing: PairingSession? = null
    val pairingState get() = pairing?.state

    val executor = CommandExecutor { control }

    init { startDiscovery() }

    fun startDiscovery() {
        discoveryJob?.cancel()
        subnetJob?.cancel()
        _status.value = "Scanning Wi-Fi\u2026"

        // 1) Fast path: mDNS/Bonjour.
        discoveryJob = viewModelScope.launch {
            discovery.discover().collect { list -> mergeWifi(list) }
        }
        // 2) Slow but reliable fallback: TCP subnet probe on port 6466.
        subnetJob = viewModelScope.launch {
            subnetScanner.scan().collect { list -> mergeWifi(list) }
        }
    }

    private fun mergeWifi(more: List<Device>) {
        val map = LinkedHashMap<String, Device>()
        _devices.value.forEach { map[it.stableId] = it }
        more.forEach { d ->
            val existing = map[d.stableId]
            map[d.stableId] = existing?.copy(name = existing.name.ifBlank { d.name }) ?: d
        }
        _devices.value = map.values.toList()
    }

    fun addManualDevice(host: String, name: String? = null) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return
        val dev = Device(name = name?.ifBlank { null } ?: "TV @ $trimmed", host = trimmed)
        mergeWifi(listOf(dev))
        _status.value = "Added $trimmed manually."
    }

    // --- Bluetooth ---
    fun startBluetoothScan() {
        if (!bluetooth.supported()) { _status.value = "Bluetooth not available."; return }
        if (!bluetooth.enabled()) { _status.value = "Enable Bluetooth to scan."; return }
        if (!bluetooth.hasPermissions()) { _status.value = "Grant Bluetooth permission to scan."; return }
        btJob?.cancel()
        _status.value = "Scanning Bluetooth\u2026"
        btJob = viewModelScope.launch {
            bluetooth.discover().collect { _btDevices.value = it }
        }
    }

    fun stopBluetoothScan() { btJob?.cancel(); btJob = null }

    fun bluetoothPair(address: String) {
        val ok = bluetooth.pair(address)
        _status.value = if (ok) "Bluetooth pairing requested. Confirm the PIN on your TV."
                        else "Bluetooth pairing failed."
    }

    fun launchUrl(url: String) = viewModelScope.launch {
        val u = url.trim(); if (u.isEmpty()) return@launch
        control?.launchAppUri(u); _status.value = "Sent to TV: $u"
    }

    fun beginPair(device: Device): PairingSession {
        val session = PairingSession(device, store)
        pairing = session
        viewModelScope.launch { session.start() }
        return session
    }

    fun submitPin(pin: String) = viewModelScope.launch {
        pairing?.submitPin(pin)?.let { ok ->
            if (ok) _status.value = "Paired with ${pairing?.state?.value}"
        }
    }

    fun connect(device: Device) = viewModelScope.launch {
        control?.close()
        val cs = ControlSession(device, store)
        control = cs
        _status.value = "Connecting to ${device.name}\u2026"
        cs.connect()
        _status.value = when (val s = cs.state.value) {
            is ControlSession.State.Connected -> "Connected to ${s.device.name}"
            is ControlSession.State.Error -> "Error: ${s.reason}"
            else -> "Disconnected"
        }
    }

    fun disconnect() {
        control?.close(); control = null; _status.value = "Disconnected"
    }

    fun forget(device: Device) {
        control?.close(); control = null
        store.clearDevice(device.stableId)
        _status.value = "Forgot ${device.name}"
    }

    fun sendKey(keyCode: Int) = viewModelScope.launch { control?.sendKey(keyCode) }
    fun sendText(text: String) = viewModelScope.launch { control?.sendText(text) }

    fun runNaturalCommand(text: String) = viewModelScope.launch {
        _lastVoice.value = text
        val intent = NlParser.parse(text)
        val msg = executor.execute(intent)
        _status.value = msg
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { themePref.set(mode) }

    val appShortcuts: List<Pair<String, String>> = AppShortcuts.byName.entries
        .distinctBy { it.value }.map { it.key.replaceFirstChar { c -> c.uppercase() } to it.value }
}
