package com.mulerun.androidtvremote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.themeDataStore by preferencesDataStore("theme_prefs")
private val KEY_THEME = stringPreferencesKey("mode")

class ThemePref(private val context: Context) {
    val mode: Flow<ThemeMode> = context.themeDataStore.data.map {
        when (it[KEY_THEME]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun set(mode: ThemeMode) {
        context.themeDataStore.edit {
            it[KEY_THEME] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }
}
