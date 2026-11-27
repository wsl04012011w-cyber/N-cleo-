package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nucleo_preferences")

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val showTokenTelemetry: Boolean = true,
    val defaultMaxTokens: Int = 1024
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_TOKEN_TELEMETRY = booleanPreferencesKey("show_token_telemetry")
        val DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        val theme = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.DARK)
        UserPreferences(
            themeMode = theme,
            showTokenTelemetry = prefs[Keys.SHOW_TOKEN_TELEMETRY] ?: true,
            defaultMaxTokens = prefs[Keys.DEFAULT_MAX_TOKENS] ?: 1024
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setShowTokenTelemetry(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TOKEN_TELEMETRY] = enabled }
    }

    suspend fun setDefaultMaxTokens(maxTokens: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_MAX_TOKENS] = maxTokens.coerceIn(256, 4096) }
    }
}
