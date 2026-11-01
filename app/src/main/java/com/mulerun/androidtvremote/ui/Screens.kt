package com.mulerun.androidtvremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulerun.androidtvremote.control.KeyCodes
import com.mulerun.androidtvremote.data.Device
import com.mulerun.androidtvremote.data.ThemeMode
import com.mulerun.androidtvremote.pairing.PairingSession

@Composable
fun DevicesScreen(
    devices: List<Device>,
    status: String,
    onRescan: () -> Unit,
    onPair: (Device) -> Unit,
    onConnect: (Device) -> Unit,
    onForget: (Device) -> Unit,
    onAddManual: (String) -> Unit,
    btDevices: List<com.mulerun.androidtvremote.discovery.BluetoothTv.BtDev>,
    onBtScan: () -> Unit,
    onBtPair: (String) -> Unit,
    onLaunchUrl: (String) -> Unit,
) {
    var manualIp by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Devices", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRescan) { Icon(Icons.Filled.Refresh, contentDescription = "Rescan") }
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Developed by Adarsh Gupta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        item { HelpTipCard() }
        item {
            SectionHeader("Wi-Fi devices")
            if (devices.isEmpty()) {
                Text(
                    "No TVs discovered yet. Make sure your TV and phone are on the same Wi-Fi. " +
                            "If discovery is blocked, use \u201cAdd by IP\u201d below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
        }
        items(devices, key = { it.stableId }) { dev ->
            DeviceCard(dev,
                onPair = { onPair(dev) },
                onConnect = { onConnect(dev) },
                onForget = { onForget(dev) })
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Add by IP address", style = MaterialTheme.typography.titleSmall)
                    Text("If the TV isn't found automatically, enter its IP shown in TV Settings → Network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualIp, onValueChange = { manualIp = it },
                            label = { Text("e.g. 192.168.1.42") },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onAddManual(manualIp); manualIp = "" },
                            enabled = manualIp.isNotBlank()) { Text("Add") }
                    }
                }
            }
        }
        item {
            SectionHeader("Bluetooth pairing")
            Text("Some TVs pair over Bluetooth. Tap Scan, then tap the TV to open the secure system PIN dialog.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onBtScan, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Scan Bluetooth")
            }
        }
        items(btDevices, key = { "bt-" + it.address }) { bt ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(bt.name, fontWeight = FontWeight.SemiBold)
                        Text(bt.address + if (bt.bonded) "  \u2022 paired" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onBtPair(bt.address) }, enabled = !bt.bonded) {
                        Text(if (bt.bonded) "Paired" else "Pair")
                    }
                }
            }
        }
        item {
            SectionHeader("Open on TV")
            Text("Send a URL, deep link, or intent URI to the connected TV — YouTube video links, web pages, or app deep links (e.g. spotify://).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("https://\u2026 or app://\u2026") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onLaunchUrl(url); url = "" }, enabled = url.isNotBlank()) { Text("Send") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun HelpTipCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Not finding your TV?", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text("\u2022 Make sure the TV and phone are on the same Wi-Fi\n" +
                    "\u2022 On the TV, enable Settings \u2192 Apps \u2192 See all \u2192 Android TV Remote Service\n" +
                    "\u2022 Some routers block mDNS \u2014 tap Rescan (it also does a subnet scan)\n" +
                    "\u2022 Still nothing? Enter the TV's IP manually below.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DeviceCard(dev: Device, onPair: () -> Unit, onConnect: () -> Unit, onForget: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dev.name, fontWeight = FontWeight.SemiBold)
                Text("${dev.host}:${dev.port}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                dev.model?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (dev.paired) {
                TextButton(onClick = onConnect) { Text("Connect") }
                TextButton(onClick = onForget) { Text("Forget") }
            } else {
                Button(onClick = onPair) { Text("Pair") }
            }
        }
    }
}

@Composable
fun PairingDialog(
    device: Device,
    state: PairingSession.State,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with ${device.name}") },
        text = {
            Column {
                Text("Enter the 4-digit PIN shown on the TV. This authorizes only this device.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.take(6) },
                    label = { Text("PIN") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text("I confirm I own this TV or have permission to control it.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                when (state) {
                    is PairingSession.State.Connecting -> Text("Connecting\u2026")
                    is PairingSession.State.AwaitingPin -> Text("Ready. Enter PIN from TV.")
                    is PairingSession.State.Verifying -> Text("Verifying\u2026")
                    is PairingSession.State.Paired -> Text("Paired!")
                    is PairingSession.State.Failed -> Text("Failed: ${state.reason}",
                        color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(enabled = consent && pin.length >= 4, onClick = { onPin(pin) }) { Text("Pair") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RemoteScreen(
    status: String,
    onKey: (Int) -> Unit,
    onLaunch: (String) -> Unit,
    onText: (String) -> Unit,
    shortcuts: List<Pair<String, String>>,
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            IconRoundBtn(Icons.Filled.PowerSettingsNew, "Power") { onKey(KeyCodes.POWER) }
            IconRoundBtn(Icons.Filled.Home, "Home") { onKey(KeyCodes.HOME) }
            IconRoundBtn(Icons.Filled.ArrowBack, "Back") { onKey(KeyCodes.BACK) }
            IconRoundBtn(Icons.Filled.Mic, "Assistant") { onKey(KeyCodes.ASSIST) }
        }
        Spacer(Modifier.height(20.dp))

        DPad(onUp = { onKey(KeyCodes.DPAD_UP) }, onDown = { onKey(KeyCodes.DPAD_DOWN) },
             onLeft = { onKey(KeyCodes.DPAD_LEFT) }, onRight = { onKey(KeyCodes.DPAD_RIGHT) },
             onOk = { onKey(KeyCodes.DPAD_CENTER) })

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            IconRoundBtn(Icons.Filled.VolumeDown, "Vol-") { onKey(KeyCodes.VOL_DOWN) }
            IconRoundBtn(Icons.Filled.VolumeOff, "Mute") { onKey(KeyCodes.VOL_MUTE) }
            IconRoundBtn(Icons.Filled.VolumeUp, "Vol+") { onKey(KeyCodes.VOL_UP) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            IconRoundBtn(Icons.Filled.FastRewind, "Rew") { onKey(KeyCodes.REWIND) }
            IconRoundBtn(Icons.Filled.PlayArrow, "Play/Pause") { onKey(KeyCodes.PLAY_PAUSE) }
            IconRoundBtn(Icons.Filled.FastForward, "FF") { onKey(KeyCodes.FAST_FORWARD) }
        }
        Spacer(Modifier.height(24.dp))
        Text("Keyboard", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Type on TV") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onText(text); text = "" }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            })
        Spacer(Modifier.height(20.dp))
        Text("Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        FlowRowShortcut(shortcuts, onLaunch)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun IconRoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(58.dp)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onOk: () -> Unit) {
    val btn = Modifier.size(64.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onUp, modifier = btn) { Icon(Icons.Filled.KeyboardArrowUp, "Up") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = onLeft, modifier = btn) { Icon(Icons.Filled.KeyboardArrowLeft, "Left") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onOk, modifier = Modifier.size(84.dp), shape = CircleShape) { Text("OK", fontSize = 18.sp) }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = onRight, modifier = btn) { Icon(Icons.Filled.KeyboardArrowRight, "Right") }
        }
        FilledIconButton(onClick = onDown, modifier = btn) { Icon(Icons.Filled.KeyboardArrowDown, "Down") }
    }
}

@Composable
private fun FlowRowShortcut(items: List<Pair<String, String>>, onLaunch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (name, uri) ->
                    AssistChip(onClick = { onLaunch(uri) }, label = { Text(name) },
                        modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun VoiceScreen(
    lastTranscript: String, status: String,
    listening: Boolean, onStart: () -> Unit, onStop: () -> Unit, onSubmitText: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Voice", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Text("Say things like \u201cTurn off the TV\u201d, \u201cOpen YouTube\u201d, or \u201cSet volume to 50 percent\u201d.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        FilledIconButton(onClick = { if (listening) onStop() else onStart() },
            modifier = Modifier.size(96.dp), shape = CircleShape) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null, modifier = Modifier.size(48.dp))
        }
        Text(if (listening) "Listening\u2026" else "Tap to speak", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(20.dp))
        if (lastTranscript.isNotBlank()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Last command", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastTranscript, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = typed, onValueChange = { typed = it },
            label = { Text("or type a command") }, singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onSubmitText(typed); typed = "" }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SettingsScreen(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeMode.values().forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { onModeChange(m) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == m, onClick = { onModeChange(m) })
                    Spacer(Modifier.width(8.dp))
                    Text(when (m) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    })
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("This app uses the official Android TV Remote v2 protocol (TLS + PIN) and mDNS discovery. " +
                "Control is only performed after the TV owner explicitly pairs the device. " +
                "All credentials are stored encrypted on-device.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(24.dp))
        DeveloperCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeveloperCard() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Developed by",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
            ) {
                Image(
                    painter = painterResource(
                        id = com.mulerun.androidtvremote.R.drawable.developer
                    ),
                    contentDescription = "Photo of Adarsh Gupta",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "ADARSH GUPTA",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Android Engineer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

