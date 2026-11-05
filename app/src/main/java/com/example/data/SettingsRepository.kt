package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "worm_copier_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_DEST_PATH = stringPreferencesKey("active_dest_path")
        val ACTIVE_DEST_URI = stringPreferencesKey("active_dest_uri")
        val SELECTED_GAME_EDITION = stringPreferencesKey("selected_game_edition")
        val AUTO_DELETE_ENABLED = booleanPreferencesKey("auto_delete_enabled")
        val DEFAULT_RETENTION_MINUTES = intPreferencesKey("default_retention_minutes")
        val AUTO_USE_SHIZUKU = booleanPreferencesKey("auto_use_shizuku")
        val CONFIRM_OVERWRITE = booleanPreferencesKey("confirm_overwrite")
        val VERIFY_SHA256 = booleanPreferencesKey("verify_sha256")
        val KEEP_HISTORY = booleanPreferencesKey("keep_history")
        val VIBRATE_ON_FAILURE = booleanPreferencesKey("vibrate_on_failure")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            activeDestinationPath = prefs[Keys.ACTIVE_DEST_PATH] ?: DEFAULT_PUFFER_TEMP_PATH,
            activeDestinationTreeUri = prefs[Keys.ACTIVE_DEST_URI],
            selectedGameEdition = prefs[Keys.SELECTED_GAME_EDITION] ?: GameEditionKey.PUBG_GLOBAL.name,
            autoDeleteEnabled = prefs[Keys.AUTO_DELETE_ENABLED] ?: true,
            defaultRetentionMinutes = prefs[Keys.DEFAULT_RETENTION_MINUTES]?.coerceIn(1, 10) ?: 3,
            autoUseShizuku = prefs[Keys.AUTO_USE_SHIZUKU] ?: true,
            confirmOverwrite = prefs[Keys.CONFIRM_OVERWRITE] ?: true,
            verifySha256 = prefs[Keys.VERIFY_SHA256] ?: true,
            keepHistory = prefs[Keys.KEEP_HISTORY] ?: true,
            vibrateOnFailure = prefs[Keys.VIBRATE_ON_FAILURE] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "ar",
            darkMode = prefs[Keys.DARK_MODE] ?: true
        )
    }

    suspend fun setActiveDestination(path: String, treeUriString: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_DEST_PATH] = path
            if (treeUriString != null) {
                prefs[Keys.ACTIVE_DEST_URI] = treeUriString
            } else {
                prefs.remove(Keys.ACTIVE_DEST_URI)
            }
        }
    }

    suspend fun setSelectedGameEdition(editionKeyName: String) {
        context.dataStore.edit { it[Keys.SELECTED_GAME_EDITION] = editionKeyName }
    }

    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DELETE_ENABLED] = enabled }
    }

    suspend fun setDefaultRetentionMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 10)
        context.dataStore.edit { it[Keys.DEFAULT_RETENTION_MINUTES] = clamped }
    }

    suspend fun setAutoUseShizuku(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_USE_SHIZUKU] = enabled }
    }

    suspend fun setConfirmOverwrite(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_OVERWRITE] = enabled }
    }

    suspend fun setVerifySha256(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VERIFY_SHA256] = enabled }
    }

    suspend fun setKeepHistory(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_HISTORY] = enabled }
    }

    suspend fun setVibrateOnFailure(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATE_ON_FAILURE] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.ACTIVE_DEST_PATH] = DEFAULT_PUFFER_TEMP_PATH
            prefs[Keys.SELECTED_GAME_EDITION] = GameEditionKey.PUBG_GLOBAL.name
            prefs[Keys.AUTO_DELETE_ENABLED] = true
            prefs[Keys.DEFAULT_RETENTION_MINUTES] = 3
            prefs[Keys.AUTO_USE_SHIZUKU] = true
            prefs[Keys.CONFIRM_OVERWRITE] = true
            prefs[Keys.VERIFY_SHA256] = true
            prefs[Keys.KEEP_HISTORY] = true
            prefs[Keys.VIBRATE_ON_FAILURE] = true
            prefs[Keys.LANGUAGE_CODE] = "ar"
            prefs[Keys.DARK_MODE] = true
        }
    }
}
