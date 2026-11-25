package com.example.server

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseClient(
    private val context: Context,
    private val sessionStore: SessionStore
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    private val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    fun isConfigured(): Boolean {
        return baseUrl.startsWith("https://") &&
            publishableKey.isNotBlank() &&
            publishableKey != "CONFIGURE_IN_SECRETS"
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun getOrCreateDeviceId(): String {
        val aid = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        } catch (_: Throwable) { null }
        if (!aid.isNullOrBlank() && aid != "9774d56d682e549c") return "aid-$aid"
        val prefs = context.getSharedPreferences("ans_device_meta", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    @Suppress("DEPRECATION")
    private fun getAppSignatureSha256(): String {
        return try {
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val pi = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                pi.signingInfo?.apkContentsSigners
            } else {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            }
            val first = sigs?.firstOrNull() ?: return ""
            java.security.MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) { "" }
    }

    // ==========================================================================
    // AUTHENTICATION (Email & Password + Token Refresh)
    // ==========================================================================

    suspend fun signInWithTelegramCode(code: String): Result<AuthSessionState> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Server key is not configured"))
            }
            try {
                val payload = JSONObject().apply { put("code", code.trim()) }
                val req = Request.Builder()
                    .url("$baseUrl/functions/v1/telegram-login")
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer $publishableKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val json = JSONObject(raw.ifBlank { "{}" })
                    if (!resp.isSuccessful || !json.optBoolean("success", false)) {
                        return@withContext Result.failure(
                            IllegalStateException(json.optString("code").ifBlank { "LOGIN_FAILED" })
                        )
                    }
                    val userObj = json.optJSONObject("user")
                    val session = AuthSessionState(
                        accessToken = json.optString("access_token"),
                        refreshToken = json.optString("refresh_token"),
                        userId = userObj?.optString("id").orEmpty(),
                        email = userObj?.optString("email").orEmpty(),
                        expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + json.optLong("expires_in", 3600L)
                    )
                    sessionStore.saveSession(session)
                    Result.success(session)
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession()
        if (isConfigured() && session.accessToken.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/logout")
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .post("{}".toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Throwable) {
            }
        }
        sessionStore.clearSession()
    }

    private suspend fun getValidAccessToken(): String? {
        val current = sessionStore.getSession()
        if (!current.isLoggedIn) return null
        val nowSec = System.currentTimeMillis() / 1000L
        if (current.expiresAtEpochSec > nowSec + 60L) {
            return current.accessToken
        }
        if (current.refreshToken.isBlank()) return current.accessToken
        return try {
            val payload = JSONObject().put("refresh_token", current.refreshToken)
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use current.accessToken
                val json = JSONObject(resp.body?.string().orEmpty())
                val refreshed = parseAuthSession(json, current.email)
                sessionStore.saveSession(refreshed)
                refreshed.accessToken
            }
        } catch (_: Throwable) {
            current.accessToken
        }
    }

    private fun parseAuthSession(json: JSONObject, fallbackEmail: String): AuthSessionState {
        val userObj = json.optJSONObject("user")
        val expiresIn = json.optLong("expires_in", 3600L)
        return AuthSessionState(
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token"),
            userId = userObj?.optString("id").orEmpty(),
            email = userObj?.optString("email").orEmpty().ifBlank { fallbackEmail },
            expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + expiresIn
        )
    }

    // ==========================================================================
    // EDGE FUNCTION: server-gate-status
    // ==========================================================================

    suspend fun fetchServerGateStatus(
        installedFiles: Map<String, InstalledServerFileMeta>
    ): ServerGateStatus = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext ServerGateStatus(
                isReachable = false,
                isConfigured = false,
                canExecuteCopy = false,
                blockReasonCode = "CONFIG_MISSING",
                blockReasonAr = "إعدادات الاتصال بالسيرفر غير مهيأة (SUPABASE_PUBLISHABLE_KEY).",
                blockReasonEn = "Server connection key is not configured.",
                errorMessage = "SUPABASE_PUBLISHABLE_KEY missing"
            )
        }

        try {
            val token = getValidAccessToken()
            val filesJson = JSONArray()
            installedFiles.values.forEach { f ->
                filesJson.put(JSONObject().apply {
                    put("id", f.contentId)
                    put("version_code", f.versionCode)
                    put("sha256", f.sha256)
                })
            }

            val bodyJson = JSONObject().apply {
                put("app_version_code", BuildConfig.VERSION_CODE)
                put("app_version", BuildConfig.VERSION_NAME)
                put("device_id", getOrCreateDeviceId())
                put("app_sig", getAppSignatureSha256())
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("client_files", filesJson)
            }

            val reqBuilder = Request.Builder()
                .url("$baseUrl/functions/v1/server-gate-status")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMedia))
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ServerGateStatus(
                        isReachable = false,
                        canExecuteCopy = false,
                        blockReasonCode = "SERVER_ERROR",
                        blockReasonAr = "تعذر الاتصال بخادم ANS (${resp.code}).",
                        blockReasonEn = "ANS server returned HTTP ${resp.code}",
                        errorMessage = raw.take(200)
                    )
                }
                val parsed = parseServerGateJson(JSONObject(raw))
                if (parsed.appUpdate == null) parsed.copy(appUpdate = fetchGithubUpdate()) else parsed
            }
        } catch (t: Throwable) {
            ServerGateStatus(
                isReachable = false,
                canExecuteCopy = false,
                blockReasonCode = "OFFLINE",
                blockReasonAr = "تعذر الوصول إلى السيرفر. تحقق من اتصالك بالإنترنت.",
                blockReasonEn = "Unable to reach ANS Server Control. Check internet connection.",
                errorMessage = t.localizedMessage
            )
        }
    }

    /** Latest APK published by the build pipeline as the GitHub release tagged "latest". */
    private fun fetchGithubUpdate(): AppUpdateInfo? {
        return try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/ansabdaiia557-eng/worm-temp-copier-aistudio/releases/tags/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val j = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val body = j.optString("body")
                val code = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                if (code <= BuildConfig.VERSION_CODE) return null
                val assets = j.optJSONArray("assets") ?: return null
                var url = ""
                var size = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); size = a.optLong("size"); break
                    }
                }
                if (url.isBlank()) return null
                AppUpdateInfo(
                    id = "github-$code",
                    versionCode = code,
                    versionName = j.optString("name").ifBlank { "build $code" },
                    changelogAr = "نسخة جديدة جاهزة. اضغط تحديث وهتتثبت فوق القديمة بدون ما تمسح حاجة.",
                    changelogEn = "A new version is ready. Tap update to install it over the old one.",
                    downloadUrl = url,
                    sha256 = "",
                    sizeBytes = size,
                    isMandatory = false
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    internal fun parseServerGateJson(json: JSONObject): ServerGateStatus {
        val maintObj = json.optJSONObject("maintenance")
        val maint = MaintenanceInfo(
            isActive = maintObj?.optBoolean("is_active", false) ?: false,
            isScheduled = maintObj?.optBoolean("is_scheduled", false) ?: false,
            reasonAr = maintObj?.optString("reason_ar")?.takeIf { it.isNotBlank() && it != "null" },
            reasonEn = maintObj?.optString("reason_en")?.takeIf { it.isNotBlank() && it != "null" },
            startedAt = maintObj?.optString("started_at")?.takeIf { it.isNotBlank() && it != "null" },
            endsAt = maintObj?.optString("ends_at")?.takeIf { it.isNotBlank() && it != "null" }
        )

        val userObj = json.optJSONObject("user")
        val user = if (userObj != null) {
            ServerUserProfile(
                id = userObj.optString("id"),
                email = userObj.optString("email"),
                displayName = userObj.optString("display_name"),
                role = userObj.optString("role", "user"),
                accountStatus = userObj.optString("account_status", "active"),
                isBanned = userObj.optBoolean("is_banned", false),
                banReason = userObj.optString("ban_reason").takeIf { it.isNotBlank() && it != "null" },
                telegramVerified = userObj.optBoolean("telegram_verified", false),
                telegramUserId = if (userObj.isNull("telegram_user_id")) null else userObj.optLong("telegram_user_id")
            )
        } else null

        val subObj = json.optJSONObject("subscription")
        val sub = ServerSubscriptionInfo(
            required = subObj?.optBoolean("required", true) ?: true,
            isActive = subObj?.optBoolean("is_active", false) ?: false,
            planName = subObj?.optString("plan_name")?.takeIf { it.isNotBlank() && it != "null" },
            status = subObj?.optString("status", "none") ?: "none",
            startsAt = subObj?.optString("starts_at")?.takeIf { it.isNotBlank() && it != "null" },
            endsAt = subObj?.optString("ends_at")?.takeIf { it.isNotBlank() && it != "null" }
        )

        val tgObj = json.optJSONObject("telegram")
        val tg = TelegramGateStatus(
            required = tgObj?.optBoolean("required", true) ?: true,
            verified = tgObj?.optBoolean("verified", false) ?: false,
            channelUrl = tgObj?.optString("channel_url", "https://t.me/Kharbash_anas") ?: "https://t.me/Kharbash_anas",
            channelUsername = tgObj?.optString("channel_username", "@Kharbash_anas") ?: "@Kharbash_anas"
        )

        val filesArr = json.optJSONArray("files") ?: JSONArray()
        val filesList = buildList {
            for (i in 0 until filesArr.length()) {
                val f = filesArr.getJSONObject(i)
                val edArr = f.optJSONArray("compatible_editions") ?: JSONArray()
                val editions = buildList {
                    for (j in 0 until edArr.length()) add(edArr.optString(j))
                }
                add(
                    ServerContentFileDto(
                        id = f.optString("id"),
                        title = f.optString("title"),
                        description = f.optString("description"),
                        category = f.optString("category", "ALL_FEATURES"),
                        version = f.optString("version", "v1.0.0"),
                        versionCode = f.optInt("version_code", 1),
                        sizeBytes = f.optLong("size_bytes", 0L),
                        updatedAt = f.optString("updated_at", "").take(10),
                        targetFilename = f.optString("target_filename"),
                        targetRelativePath = f.optString("target_relative_path", ""),
                        sha256 = f.optString("sha256"),
                        storagePath = f.optString("storage_path").takeIf { it.isNotBlank() && it != "null" },
                        compatibleEditions = editions,
                        isRequired = f.optBoolean("is_required", true),
                        targetGroup = f.optString("target_group", "all")
                    )
                )
            }
        }

        val outdatedArr = json.optJSONArray("outdated_file_ids") ?: JSONArray()
        val outdatedIds = buildList {
            for (i in 0 until outdatedArr.length()) add(outdatedArr.optString(i))
        }

        val updObj = json.optJSONObject("app_update")
        val appUpdate = if (updObj != null) {
            AppUpdateInfo(
                id = updObj.optString("id"),
                versionCode = updObj.optInt("version_code", 0),
                versionName = updObj.optString("version_name"),
                changelogAr = updObj.optString("changelog_ar"),
                changelogEn = updObj.optString("changelog_en"),
                downloadUrl = updObj.optString("download_url"),
                sha256 = updObj.optString("sha256"),
                sizeBytes = updObj.optLong("size_bytes", 0L),
                isMandatory = updObj.optBoolean("is_mandatory", false)
            )
        } else null

        return ServerGateStatus(
            isReachable = true,
            isConfigured = true,
            serverTime = json.optString("server_time"),
            serverName = json.optString("server_name", "ANS SERVER CONTROL"),
            globalFileRevision = json.optInt("global_file_revision", 1),
            canExecuteCopy = json.optBoolean("can_execute_copy", false),
            blockReasonCode = json.optString("block_reason_code").takeIf { it.isNotBlank() && it != "null" },
            blockReasonAr = json.optString("block_reason_ar").takeIf { it.isNotBlank() && it != "null" },
            blockReasonEn = json.optString("block_reason_en").takeIf { it.isNotBlank() && it != "null" },
            maintenance = maint,
            user = user,
            subscription = sub,
            telegram = tg,
            files = filesList,
            outdatedFileIds = outdatedIds,
            appUpdate = appUpdate
        )
    }

    // ==========================================================================
    // EDGE FUNCTION: redeem-activation-code
    // ==========================================================================

    suspend fun redeemActivationCode(code: String): ActivationResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext ActivationResult(false, "Server configuration missing")
        }
        val token = getValidAccessToken()
            ?: return@withContext ActivationResult(false, "Please sign in first")

        try {
            val payload = JSONObject().put("code", code.trim().uppercase())
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/redeem-activation-code")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val ok = resp.isSuccessful && json.optBoolean("success", false)
                val sub = json.optJSONObject("subscription")
                ActivationResult(
                    success = ok,
                    message = json.optString("message").ifBlank { json.optString("error", "Redemption failed") },
                    planName = sub?.optString("plan_name"),
                    endsAt = sub?.optString("ends_at")
                )
            }
        } catch (t: Throwable) {
            ActivationResult(false, t.localizedMessage ?: "Network error")
        }
    }

    // ==========================================================================
    // EDGE FUNCTION: verify-telegram-membership
    // ==========================================================================

    suspend fun verifyTelegramMembership(telegramUserId: String): TelegramVerifyResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext TelegramVerifyResult(false, false, message = "Server not configured")
            }
            val token = getValidAccessToken()
                ?: return@withContext TelegramVerifyResult(false, false, message = "Sign in required")

            try {
                val payload = JSONObject().put("telegram_user_id", telegramUserId.trim())
                val req = Request.Builder()
                    .url("$baseUrl/functions/v1/verify-telegram-membership")
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                    val isMember = json.optBoolean("is_member", false)
                    val ok = json.optBoolean("success", false)
                    val msg = json.optString("error").ifBlank {
                        if (isMember) "Verified channel membership" else "Not yet a member of the channel"
                    }
                    TelegramVerifyResult(
                        success = ok,
                        isMember = isMember,
                        status = json.optString("status"),
                        channelUrl = json.optString("channel_url", "https://t.me/Kharbash_anas"),
                        message = msg
                    )
                }
            } catch (t: Throwable) {
                TelegramVerifyResult(false, false, message = t.localizedMessage ?: "Verification error")
            }
        }

    // ==========================================================================
    // EDGE FUNCTION: get-download-url + Verified Binary Download
    // ==========================================================================

    suspend fun requestSignedDownloadUrl(contentId: String): Result<SignedDownloadPayload> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Server not configured"))
            }
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Authentication required"))

            try {
                val payload = JSONObject().put("content_id", contentId).put("device_id", getOrCreateDeviceId()).put("app_sig", getAppSignatureSha256()).put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                val req = Request.Builder()
                    .url("$baseUrl/functions/v1/get-download-url")
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                    if (!resp.isSuccessful || !json.optBoolean("success", false)) {
                        val err = json.optString("error_ar").ifBlank {
                            json.optString("error", "Download authorization failed (${resp.code})")
                        }
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                    Result.success(
                        SignedDownloadPayload(
                            contentId = json.optString("content_id"),
                            title = json.optString("title"),
                            targetFilename = json.optString("target_filename"),
                            targetRelativePath = json.optString("target_relative_path", ""),
                            version = json.optString("version", "v1.0.0"),
                            versionCode = json.optInt("version_code", 1),
                            sha256 = json.optString("sha256"),
                            sizeBytes = json.optLong("size_bytes", 0L),
                            signedUrl = json.optString("signed_url"),
                            expiresInSeconds = json.optInt("expires_in_seconds", 3600)
                        )
                    )
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun downloadAndVerifyBinary(
        signedUrl: String,
        destinationFile: File,
        expectedSha256: String,
        expectedSizeBytes: Long,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(signedUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Download HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(IllegalStateException("Empty response body"))
                val total = body.contentLength().takeIf { it > 0 } ?: expectedSizeBytes
                body.byteStream().use { input ->
                    verifyAndWriteStream(
                        input = input,
                        totalBytesHint = total,
                        destinationFile = destinationFile,
                        expectedSha256 = expectedSha256,
                        expectedSizeBytes = expectedSizeBytes,
                        onProgress = onProgress
                    )
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal fun verifyAndWriteStream(
        input: java.io.InputStream,
        totalBytesHint: Long,
        destinationFile: File,
        expectedSha256: String,
        expectedSizeBytes: Long,
        onProgress: (Float) -> Unit
    ): Result<String> {
        destinationFile.parentFile?.mkdirs()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.worm_dl_tmp")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L
            val buf = ByteArray(32 * 1024)

            FileOutputStream(tempFile).use { output ->
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    bytesCopied += read
                    if (totalBytesHint > 0) {
                        onProgress((bytesCopied.toFloat() / totalBytesHint.toFloat()).coerceIn(0.05f, 0.95f))
                    }
                }
                output.fd.sync()
            }

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSizeBytes > 0L && bytesCopied != expectedSizeBytes) {
                tempFile.delete()
                return Result.failure(
                    SecurityException("File size mismatch: expected $expectedSizeBytes bytes, got $bytesCopied bytes")
                )
            }
            if (expectedSha256.isNotBlank() && !actualSha.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                return Result.failure(
                    SecurityException("SHA-256 integrity mismatch! File rejected.")
                )
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            onProgress(1.0f)
            Result.success(actualSha)
        } catch (t: Throwable) {
            tempFile.delete()
            Result.failure(t)
        }
    }

    // ==========================================================================
    // EDGE FUNCTION: app-update-check & record-operation-audit
    // ==========================================================================

    suspend fun checkAppUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(null)
        try {
            val payload = JSONObject().put("current_version_code", BuildConfig.VERSION_CODE)
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/app-update-check")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.success(null)
                val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                if (!json.optBoolean("update_available", false)) return@withContext Result.success(null)
                val u = json.optJSONObject("latest") ?: return@withContext Result.success(null)
                Result.success(
                    AppUpdateInfo(
                        id = u.optString("id"),
                        versionCode = u.optInt("version_code", 0),
                        versionName = u.optString("version_name"),
                        changelogAr = u.optString("changelog_ar"),
                        changelogEn = u.optString("changelog_en"),
                        downloadUrl = u.optString("download_url"),
                        sha256 = u.optString("sha256"),
                        sizeBytes = u.optLong("size_bytes", 0L),
                        isMandatory = u.optBoolean("is_mandatory", false)
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun recordOperationAudit(
        action: String,
        status: String,
        details: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext
        val token = getValidAccessToken() ?: return@withContext
        try {
            val detailsJson = JSONObject()
            details.forEach { (k, v) -> detailsJson.put(k, v) }
            val payload = JSONObject().apply {
                put("action", action)
                put("status", status)
                put("details", detailsJson)
            }
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/record-operation-audit")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Throwable) {
        }
    }
}
