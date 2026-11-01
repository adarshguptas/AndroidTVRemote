package com.mulerun.androidtvremote.voice

import com.mulerun.androidtvremote.control.AppShortcuts

/**
 * On-device natural-language command parser.
 *
 * Uses a layered grammar:
 *  1. Regex intent extractors run first (fast, high precision).
 *  2. Keyword scoring fills ambiguous cases (e.g. "louder" -> volume up).
 *  3. Numeric normalisation converts spoken numbers ("fifty percent") to ints.
 */
object NlParser {

    fun parse(rawTranscript: String): RemoteIntent {
        val text = rawTranscript.lowercase().trim()
        if (text.isEmpty()) return RemoteIntent.Unknown(rawTranscript)

        volumeSet(text)?.let { return it }
        typeText(text)?.let { return it }
        launchApp(text)?.let { return it }

        return when {
            match(text, "turn off", "power off", "shut down", "shutdown", "shut it down") -> RemoteIntent.PowerOff
            match(text, "turn on", "power on", "wake up", "wake") -> RemoteIntent.PowerOn
            match(text, "toggle power", "power toggle") -> RemoteIntent.PowerToggle
            match(text, "mute", "silence", "quiet") -> RemoteIntent.Mute
            match(text, "louder", "volume up", "turn it up", "turn up", "increase volume") -> RemoteIntent.VolumeUp
            match(text, "quieter", "volume down", "turn it down", "turn down", "decrease volume", "lower volume") -> RemoteIntent.VolumeDown
            match(text, "go home", "home screen", "home") -> RemoteIntent.Home
            match(text, "go back", "back") -> RemoteIntent.Back
            match(text, "scroll up", "up") -> RemoteIntent.Up
            match(text, "scroll down", "down") -> RemoteIntent.Down
            match(text, "left") -> RemoteIntent.Left
            match(text, "right") -> RemoteIntent.Right
            match(text, "select", "ok", "enter", "confirm", "click") -> RemoteIntent.Ok
            match(text, "play", "pause", "resume") -> RemoteIntent.PlayPause
            match(text, "next", "skip") -> RemoteIntent.Next
            match(text, "previous", "back a track", "last track") -> RemoteIntent.Previous
            match(text, "stop") -> RemoteIntent.Stop
            match(text, "rewind") -> RemoteIntent.Rewind
            match(text, "fast forward", "forward") -> RemoteIntent.FastForward
            match(text, "channel up", "next channel") -> RemoteIntent.ChannelUp
            match(text, "channel down", "previous channel", "last channel") -> RemoteIntent.ChannelDown
            match(text, "assistant", "hey google", "ok google") -> RemoteIntent.Assistant
            else -> RemoteIntent.Unknown(rawTranscript)
        }
    }

    private fun match(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    private fun volumeSet(text: String): RemoteIntent.VolumeSet? {
        val re = Regex("""(?:set\s+)?volume\s+(?:to|at)?\s*(\d{1,3})\s*(?:percent|%)?""")
        re.find(text)?.let { m ->
            val v = m.groupValues[1].toIntOrNull() ?: return null
            return RemoteIntent.VolumeSet(v.coerceIn(0, 100))
        }
        val spoken = Regex("""volume\s+(?:to|at)\s+([a-z\- ]+?)(?:\s+percent|\s*%)?""")
        spoken.find(text)?.let { m ->
            wordsToInt(m.groupValues[1])?.let { return RemoteIntent.VolumeSet(it.coerceIn(0, 100)) }
        }
        return null
    }

    private fun launchApp(text: String): RemoteIntent.LaunchApp? {
        val re = Regex("""(?:open|launch|start|play(?:\s+on)?)\s+([a-z0-9 \+\-]+?)(?:\s+app)?$""")
        val m = re.find(text) ?: return null
        val name = m.groupValues[1].trim()
        val uri = AppShortcuts.resolve(name) ?: return null
        return RemoteIntent.LaunchApp(name, uri)
    }

    private fun typeText(text: String): RemoteIntent.TypeText? {
        val re = Regex("""(?:type|enter|search for|write)\s+"?([^"]+?)"?$""")
        val m = re.find(text) ?: return null
        return RemoteIntent.TypeText(m.groupValues[1].trim())
    }

    private val ones = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private fun wordsToInt(phrase: String): Int? {
        val tokens = phrase.trim().split(Regex("[\\s\\-]+"))
        if (tokens.isEmpty()) return null
        if (tokens.size == 1) return ones[tokens[0]] ?: tens[tokens[0]] ?: if (tokens[0] == "hundred") 100 else null
        if (tokens.size == 2 && tokens[1] == "hundred") return (ones[tokens[0]] ?: return null) * 100
        val a = tens[tokens[0]] ?: return null
        val b = ones[tokens[1]] ?: return null
        return a + b
    }
}
