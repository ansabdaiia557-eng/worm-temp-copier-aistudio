package com.example.server

data class AuthSessionState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = "",
    val expiresAtEpochSec: Long = 0L
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank() && userId.isNotBlank()
}

data class MaintenanceInfo(
    val isActive: Boolean = false,
    val isScheduled: Boolean = false,
    val reasonAr: String? = null,
    val reasonEn: String? = null,
    val startedAt: String? = null,
    val endsAt: String? = null
)

data class ServerUserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val accountStatus: String,
    val isBanned: Boolean,
    val banReason: String?,
    val telegramVerified: Boolean,
    val telegramUserId: Long?
)

data class ServerSubscriptionInfo(
    val required: Boolean = true,
    val isActive: Boolean = false,
    val planName: String? = null,
    val status: String = "none",
    val startsAt: String? = null,
    val endsAt: String? = null
)

data class TelegramGateStatus(
    val required: Boolean = true,
    val verified: Boolean = false,
    val channelUrl: String = "https://t.me/Kharbash_anas",
    val channelUsername: String = "@Kharbash_anas"
)

data class AppUpdateInfo(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val changelogAr: String,
    val changelogEn: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val isMandatory: Boolean
)

data class ServerContentFileDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val version: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val updatedAt: String,
    val targetFilename: String,
    val targetRelativePath: String,
    val sha256: String,
    val storagePath: String?,
    val compatibleEditions: List<String>,
    val isRequired: Boolean,
    val targetGroup: String
)

data class ServerGateStatus(
    val isReachable: Boolean = false,
    val isConfigured: Boolean = true,
    val serverTime: String = "",
    val serverName: String = "ANS SERVER CONTROL",
    val globalFileRevision: Int = 1,
    val canExecuteCopy: Boolean = false,
    val blockReasonCode: String? = null,
    val blockReasonAr: String? = null,
    val blockReasonEn: String? = null,
    val maintenance: MaintenanceInfo = MaintenanceInfo(),
    val user: ServerUserProfile? = null,
    val subscription: ServerSubscriptionInfo = ServerSubscriptionInfo(),
    val telegram: TelegramGateStatus = TelegramGateStatus(),
    val files: List<ServerContentFileDto> = emptyList(),
    val outdatedFileIds: List<String> = emptyList(),
    val appUpdate: AppUpdateInfo? = null,
    val errorMessage: String? = null
)

data class InstalledServerFileMeta(
    val contentId: String,
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val localCachePath: String,
    val installedDestPath: String = "",
    val installedAtMillis: Long = 0L,
    val sizeBytes: Long = 0L
)

data class SignedDownloadPayload(
    val contentId: String,
    val title: String,
    val targetFilename: String,
    val targetRelativePath: String,
    val version: String,
    val versionCode: Int,
    val sha256: String,
    val sizeBytes: Long,
    val signedUrl: String,
    val expiresInSeconds: Int
)

data class ActivationResult(
    val success: Boolean,
    val message: String,
    val planName: String? = null,
    val endsAt: String? = null
)

data class TelegramVerifyResult(
    val success: Boolean,
    val isMember: Boolean,
    val status: String? = null,
    val channelUrl: String = "https://t.me/Kharbash_anas",
    val message: String = ""
)

data class PubgCopyInstallState(
    val isRunning: Boolean = false,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 10,
    val stepTitle: String = "",
    val progress: Float = 0f,
    val completedFileCount: Int = 0,
    val totalFileCount: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val canLaunchPubg: Boolean = false,
    val lastInstalledEditionTitle: String? = null
)
