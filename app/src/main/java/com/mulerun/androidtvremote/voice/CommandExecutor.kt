package com.mulerun.androidtvremote.voice

import com.mulerun.androidtvremote.control.ControlSession
import com.mulerun.androidtvremote.control.KeyCodes

/**
 * Bridges a parsed [RemoteIntent] to the currently connected [ControlSession].
 */
class CommandExecutor(private val session: () -> ControlSession?) {

    suspend fun execute(intent: RemoteIntent): String {
        val s = session() ?: return "Not connected to a TV yet."
        return when (intent) {
            RemoteIntent.PowerOff, RemoteIntent.PowerOn, RemoteIntent.PowerToggle -> {
                s.sendKey(KeyCodes.POWER); "Sent power key."
            }
            RemoteIntent.VolumeUp -> { s.sendKey(KeyCodes.VOL_UP); "Volume up." }
            RemoteIntent.VolumeDown -> { s.sendKey(KeyCodes.VOL_DOWN); "Volume down." }
            RemoteIntent.Mute -> { s.sendKey(KeyCodes.VOL_MUTE); "Muted." }
            is RemoteIntent.VolumeSet -> { s.setVolumePercent(intent.percent); "Set volume to ${intent.percent}%." }
            RemoteIntent.Home -> { s.sendKey(KeyCodes.HOME); "Home." }
            RemoteIntent.Back -> { s.sendKey(KeyCodes.BACK); "Back." }
            RemoteIntent.Up -> { s.sendKey(KeyCodes.DPAD_UP); "Up." }
            RemoteIntent.Down -> { s.sendKey(KeyCodes.DPAD_DOWN); "Down." }
            RemoteIntent.Left -> { s.sendKey(KeyCodes.DPAD_LEFT); "Left." }
            RemoteIntent.Right -> { s.sendKey(KeyCodes.DPAD_RIGHT); "Right." }
            RemoteIntent.Ok -> { s.sendKey(KeyCodes.DPAD_CENTER); "OK." }
            RemoteIntent.PlayPause -> { s.sendKey(KeyCodes.PLAY_PAUSE); "Play/Pause." }
            RemoteIntent.Next -> { s.sendKey(KeyCodes.NEXT); "Next." }
            RemoteIntent.Previous -> { s.sendKey(KeyCodes.PREV); "Previous." }
            RemoteIntent.Stop -> { s.sendKey(KeyCodes.STOP); "Stop." }
            RemoteIntent.Rewind -> { s.sendKey(KeyCodes.REWIND); "Rewind." }
            RemoteIntent.FastForward -> { s.sendKey(KeyCodes.FAST_FORWARD); "Fast forward." }
            RemoteIntent.ChannelUp -> { s.sendKey(KeyCodes.CHANNEL_UP); "Channel up." }
            RemoteIntent.ChannelDown -> { s.sendKey(KeyCodes.CHANNEL_DOWN); "Channel down." }
            RemoteIntent.Assistant -> { s.sendKey(KeyCodes.ASSIST); "Opened assistant." }
            is RemoteIntent.LaunchApp -> { s.launchAppUri(intent.uri); "Launching ${intent.name}." }
            is RemoteIntent.TypeText -> { s.sendText(intent.text); "Typed \"${intent.text}\"." }
            is RemoteIntent.Unknown -> "Sorry, I didn't understand: \"${intent.transcript}\"."
        }
    }
}
