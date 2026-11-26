package com.example.server

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.serverSessionStore: DataStore<Preferences> by preferencesDataStore(name = "ans_server_session")

class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val EXPIRES_AT = longPreferencesKey("expires_at_sec")
        val INSTALLED_FILES_JSON = stringPreferencesKey("installed_files_json")
        val LAST_GLOBAL_REV = intPreferencesKey("last_global_file_revision")
        val LAST_VERIFIED_EDITION = stringPreferencesKey("last_verified_pubg_edition")
        val LAST_VERIFIED_COPY_AT = longPreferencesKey("last_verified_copy_at")
        val DISMISSED_UPDATE_CODE = intPreferencesKey("dismissed_optional_update_code")
    }

    val sessionFlow: Flow<AuthSessionState> = context.serverSessionStore.data.map { prefs ->
        AuthSessionState(
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
            userId = prefs[Keys.USER_ID] ?: "",
            email = prefs[Keys.USER_EMAIL] ?: "",
            expiresAtEpochSec = prefs[Keys.EXPIRES_AT] ?: 0L
        )
    }

    val installedFilesFlow: Flow<Map<String, InstalledServerFileMeta>> = context.serverSessionStore.data.map { prefs ->
        parseInstalledFilesJson(prefs[Keys.INSTALLED_FILES_JSON] ?: "[]")
    }

    val lastVerifiedPubgEditionFlow: Flow<String?> = context.serverSessionStore.data.map { prefs ->
        prefs[Keys.LAST_VERIFIED_EDITION]
    }

    suspend fun getDismissedUpdateCode(): Int =
        context.serverSessionStore.data.first()[Keys.DISMISSED_UPDATE_CODE] ?: 0

    suspend fun setDismissedUpdateCode(code: Int) {
        context.serverSessionStore.edit { prefs -> prefs[Keys.DISMISSED_UPDATE_CODE] = code }
    }

    suspend fun getSession(): AuthSessionState = sessionFlow.first()

    suspend fun saveSession(session: AuthSessionState) {
        context.serverSessionStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
            prefs[Keys.EXPIRES_AT] = session.expiresAtEpochSec
        }
    }

    suspend fun clearSession() {
        context.serverSessionStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.EXPIRES_AT)
        }
    }

    suspend fun getInstalledFiles(): Map<String, InstalledServerFileMeta> {
        val raw = context.serverSessionStore.data.first()[Keys.INSTALLED_FILES_JSON] ?: "[]"
        return parseInstalledFilesJson(raw)
    }

    suspend fun saveInstalledFile(meta: InstalledServerFileMeta) {
        context.serverSessionStore.edit { prefs ->
            val map = parseInstalledFilesJson(prefs[Keys.INSTALLED_FILES_JSON] ?: "[]").toMutableMap()
            map[meta.contentId] = meta
            prefs[Keys.INSTALLED_FILES_JSON] = serializeInstalledFilesJson(map)
        }
    }

    suspend fun markPubgInstallVerified(editionKey: String, globalRevision: Int) {
        context.serverSessionStore.edit { prefs ->
            prefs[Keys.LAST_VERIFIED_EDITION] = editionKey
            prefs[Keys.LAST_VERIFIED_COPY_AT] = System.currentTimeMillis()
            prefs[Keys.LAST_GLOBAL_REV] = globalRevision
        }
    }

    suspend fun clearPubgInstallVerified() {
        context.serverSessionStore.edit { prefs ->
            prefs.remove(Keys.LAST_VERIFIED_EDITION)
            prefs.remove(Keys.LAST_VERIFIED_COPY_AT)
        }
    }

    private fun parseInstalledFilesJson(raw: String): Map<String, InstalledServerFileMeta> {
        val result = mutableMapOf<String, InstalledServerFileMeta>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("contentId")
                if (id.isNotBlank()) {
                    result[id] = InstalledServerFileMeta(
                        contentId = id,
                        versionCode = obj.optInt("versionCode", 1),
                        versionName = obj.optString("versionName", "v1.0.0"),
                        sha256 = obj.optString("sha256", ""),
                        localCachePath = obj.optString("localCachePath", ""),
                        installedDestPath = obj.optString("installedDestPath", ""),
                        installedAtMillis = obj.optLong("installedAtMillis", 0L),
                        sizeBytes = obj.optLong("sizeBytes", 0L)
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun serializeInstalledFilesJson(map: Map<String, InstalledServerFileMeta>): String {
        val arr = JSONArray()
        map.values.forEach { m ->
            val obj = JSONObject()
            obj.put("contentId", m.contentId)
            obj.put("versionCode", m.versionCode)
            obj.put("versionName", m.versionName)
            obj.put("sha256", m.sha256)
            obj.put("localCachePath", m.localCachePath)
            obj.put("installedDestPath", m.installedDestPath)
            obj.put("installedAtMillis", m.installedAtMillis)
            obj.put("sizeBytes", m.sizeBytes)
            arr.put(obj)
        }
        return arr.toString()
    }
}
