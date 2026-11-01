package com.mulerun.androidtvremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mulerun.androidtvremote.data.Device
import com.mulerun.androidtvremote.data.ThemeMode
import com.mulerun.androidtvremote.pairing.PairingSession
import com.mulerun.androidtvremote.ui.AppTheme
import com.mulerun.androidtvremote.ui.DevicesScreen
import com.mulerun.androidtvremote.ui.PairingDialog
import com.mulerun.androidtvremote.ui.RemoteScreen
import com.mulerun.androidtvremote.ui.RemoteViewModel
import com.mulerun.androidtvremote.ui.SettingsScreen
import com.mulerun.androidtvremote.ui.VoiceScreen
import com.mulerun.androidtvremote.voice.VoiceRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RootUi() }
    }
}

@Composable
private fun RootUi(vm: RemoteViewModel = viewModel()) {
    val themeMode by vm.theme.collectAsStateWithLifecycle()
    val darkOverride: Boolean? = when (themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    AppTheme(darkOverride = darkOverride) { MainScaffold(vm) }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(vm: RemoteViewModel) {
    val tabs = listOf(
        Tab("Devices", Icons.Filled.Devices),
        Tab("Remote", Icons.Filled.Gamepad),
        Tab("Voice", Icons.Filled.Mic),
        Tab("Settings", Icons.Filled.Settings),
    )
    var index by rememberSaveable { mutableIntStateOf(0) }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val lastVoice by vm.lastVoice.collectAsStateWithLifecycle()
    val btDevices by vm.btDevices.collectAsStateWithLifecycle()

    var pairingDevice by remember { mutableStateOf<Device?>(null) }
    val pairingStateFlow = vm.pairingState
    val pairingState: PairingSession.State =
        if (pairingStateFlow != null) pairingStateFlow.collectAsStateWithLifecycle().value
        else PairingSession.State.Idle

    val context = LocalContext.current
    val listening = remember { mutableStateOf(false) }
    val recogniser = remember { VoiceRecognizer(context) }
    val scope = rememberCoroutineScope()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice(vm, recogniser, listening, scope)
    }
    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.all { it }) vm.startBluetoothScan() }
    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> ensureBluetoothScan(context, vm, btPermLauncher) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = index == i, onClick = { index = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (index) {
                0 -> DevicesScreen(
                    devices = devices, status = status,
                    onRescan = { vm.startDiscovery() },
                    onPair = { dev -> pairingDevice = dev; vm.beginPair(dev) },
                    onConnect = { vm.connect(it) },
                    onForget = { vm.forget(it) },
                    onAddManual = { vm.addManualDevice(it) },
                    btDevices = btDevices,
                    onBtScan = {
                        if (!vm.bluetooth.supported()) return@DevicesScreen
                        if (!vm.bluetooth.enabled()) {
                            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } else if (!vm.bluetooth.hasPermissions()) {
                            btPermLauncher.launch(vm.bluetooth.requiredPermissions())
                        } else vm.startBluetoothScan()
                    },
                    onBtPair = { vm.bluetoothPair(it) },
                    onLaunchUrl = { vm.launchUrl(it) },
                )
                1 -> RemoteScreen(
                    status = status,
                    onKey = { vm.sendKey(it) },
                    onLaunch = { uri -> scope.launch { vm.executor.execute(
                        com.mulerun.androidtvremote.voice.RemoteIntent.LaunchApp("app", uri)
                    ) } },
                    onText = { vm.sendText(it) },
                    shortcuts = vm.appShortcuts,
                )
                2 -> VoiceScreen(
                    lastTranscript = lastVoice, status = status,
                    listening = listening.value,
                    onStart = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                            startVoice(vm, recogniser, listening, scope)
                        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { listening.value = false },
                    onSubmitText = { vm.runNaturalCommand(it) },
                )
                3 -> SettingsScreen(mode = theme, onModeChange = { vm.setTheme(it) })
            }

            if (pairingDevice != null && pairingState !is PairingSession.State.Paired) {
                PairingDialog(
                    device = pairingDevice!!, state = pairingState,
                    onPin = { vm.submitPin(it) },
                    onDismiss = { pairingDevice = null },
                )
            } else if (pairingState is PairingSession.State.Paired) {
                LaunchedEffect(pairingState) {
                    val paired = pairingState
                    pairingDevice = null
                    vm.connect(paired.device)
                }
            }
        }
    }
}

private fun ensureBluetoothScan(
    context: android.content.Context,
    vm: RemoteViewModel,
    permLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    if (!vm.bluetooth.enabled()) return
    if (!vm.bluetooth.hasPermissions()) permLauncher.launch(vm.bluetooth.requiredPermissions())
    else vm.startBluetoothScan()
}

private fun startVoice(
    vm: RemoteViewModel,
    recogniser: VoiceRecognizer,
    listening: MutableState<Boolean>,
    scope: CoroutineScope,
) {
    listening.value = true
    scope.launch {
        recogniser.listen().collect { ev ->
            when (ev) {
                is VoiceRecognizer.Event.Final -> {
                    listening.value = false
                    if (ev.text.isNotBlank()) vm.runNaturalCommand(ev.text)
                }
                is VoiceRecognizer.Event.Error -> listening.value = false
                else -> Unit
            }
        }
    }
}
