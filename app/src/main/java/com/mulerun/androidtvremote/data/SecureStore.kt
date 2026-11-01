package com.mulerun.androidtvremote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key/value storage for user consent flags, PIN-derived secrets,
 * and per-device client certificates/keys used by the Android TV Remote v2 protocol.
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "tvremote_secure",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)

    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun getBool(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun clearDevice(id: String) {
        prefs.edit().apply {
            remove("cert:$id"); remove("key:$id"); remove("consent:$id")
        }.apply()
    }
}
