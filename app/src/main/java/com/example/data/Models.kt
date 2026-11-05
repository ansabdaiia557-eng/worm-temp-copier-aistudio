package com.example.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

const val DEFAULT_PUFFER_TEMP_PATH =
    "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"

// 1. Game Editions Data Models
enum class GameEditionKey {
    PUBG_GLOBAL,
    PUBG_KOREA,
    PUBG_VIETNAM,
    PUBG_TAIWAN,
    PUBG_FLASH
}

data class GameEditionInfo(
    val key: GameEditionKey,
    val title: String,
    val packageName: String,
    val defaultPufferPath: String,
    val isProposedOnly: Boolean = false // e.g. PUBG FLASH is proposed until verified
)

val PRESET_GAME_EDITIONS = listOf(
    GameEditionInfo(
        key = GameEditionKey.PUBG_GLOBAL,
        title = "PUBG MOBILE GLOBAL",
        packageName = "com.tencent.ig",
        defaultPufferPath = "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"
    ),
    GameEditionInfo(
        key = GameEditionKey.PUBG_KOREA,
        title = "PUBG MOBILE KOREA",
        packageName = "com.pubg.krmobile",
        defaultPufferPath = "/storage/emulated/0/Android/data/com.pubg.krmobile/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"
    ),
    GameEditionInfo(
        key = GameEditionKey.PUBG_VIETNAM,
        title = "PUBG MOBILE VIETNAM",
        packageName = "com.vng.pubgmobile",
        defaultPufferPath = "/storage/emulated/0/Android/data/com.vng.pubgmobile/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"
    ),
    GameEditionInfo(
        key = GameEditionKey.PUBG_TAIWAN,
        title = "PUBG MOBILE TAIWAN",
        packageName = "com.rekoo.pubgm",
        defaultPufferPath = "/storage/emulated/0/Android/data/com.rekoo.pubgm/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"
    ),
    GameEditionInfo(
        key = GameEditionKey.PUBG_FLASH,
        title = "PUBG MOBILE FLASH",
        packageName = "com.tencent.igfit",
        defaultPufferPath = "/storage/emulated/0/Android/data/com.tencent.igfit/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp",
        isProposedOnly = true
    )
)

enum class GamePathAccessState {
    AVAILABLE,
    PERMISSION_REQUIRED,
    NOT_FOUND
}

enum class OperationType {
    COPY,
    MOVE
}

enum class OperationStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
    SKIPPED
}

enum class PathAccessStatus {
    DIRECT_WRITABLE,
    WILL_CREATE_DIR,
    NEEDS_SHIZUKU,
    INVALID
}

enum class FileSortOption {
    NAME,
    SIZE,
    DATE
}

data class SelectedFileItem(
    val id: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val uri: Uri? = null,
    val localFilePath: String? = null,
    val isSelected: Boolean = true,
    val computedSha256: String? = null
) {
    val displayPath: String
        get() = localFilePath ?: uri?.path ?: name
}

@Entity(tableName = "saved_paths")
data class SavedPathEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val treeUriString: String? = null,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)

@Entity(tableName = "operation_history")
data class OperationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val operationType: String, // "COPY" or "MOVE"
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long,
    val status: String, // "SUCCESS", "FAILED", "CANCELLED", "SKIPPED"
    val sha256Original: String? = null,
    val sha256Copied: String? = null,
    val sha256Verified: Boolean = false,
    val usedShizuku: Boolean = false,
    val errorMessage: String? = null
)

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val details: String? = null
)

// Scheduled Deletion Entity (WorkManager & Room backed)
@Entity(tableName = "scheduled_deletions")
data class ScheduledDeletionEntity(
    @PrimaryKey val uniqueCopyId: String, // UUID for the exact copy
    val fileName: String,
    val destinationPath: String, // Full file path inside destination folder
    val destinationTreeUri: String? = null, // SAF directory tree URI if used
    val targetFileUri: String? = null, // Direct DocumentFile URI if created via SAF
    val fileSize: Long,
    val expectedSha256: String?, // Checksum of copy to verify identity
    val gameEditionName: String, // e.g. PUBG MOBILE GLOBAL
    val durationMinutes: Int, // 1..10 minutes
    val copiedAtMillis: Long,
    val deleteAtMillis: Long, // timestamp when it should be deleted
    val isDeleted: Boolean = false,
    val cancelled: Boolean = false,
    val failureReason: String? = null
)

data class AppSettings(
    val activeDestinationPath: String = DEFAULT_PUFFER_TEMP_PATH,
    val activeDestinationTreeUri: String? = null,
    val selectedGameEdition: String = GameEditionKey.PUBG_GLOBAL.name,
    val autoDeleteEnabled: Boolean = true,
    val defaultRetentionMinutes: Int = 3, // 1..10
    val autoUseShizuku: Boolean = true,
    val confirmOverwrite: Boolean = true,
    val verifySha256: Boolean = true,
    val keepHistory: Boolean = true,
    val vibrateOnFailure: Boolean = true,
    val languageCode: String = "ar",
    val darkMode: Boolean = true
)

data class FileTransferItemResult(
    val fileName: String,
    val fileSize: Long,
    val status: OperationStatus,
    val sha256Verified: Boolean,
    val originalHash: String?,
    val targetHash: String?,
    val message: String,
    val uniqueCopyId: String? = null,
    val targetFullPath: String? = null,
    val targetFileUri: String? = null
)

data class TransferProgressState(
    val isRunning: Boolean = false,
    val operationType: OperationType = OperationType.COPY,
    val currentFileName: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val overallProgress: Float = 0f, // 0f..1f
    val isVerifyingHash: Boolean = false,
    val itemResults: List<FileTransferItemResult> = emptyList(),
    val summaryMessage: String? = null,
    val hasError: Boolean = false
)

enum class ContentCategory {
    ALL_FEATURES,
    SKINS,
    AIM_SETTINGS
}

data class ContentItem(
    val id: String,
    val title: String,
    val description: String,
    val category: ContentCategory,
    val version: String,
    val sizeBytes: Long,
    val updatedAt: String,
    val targetFileName: String,
    val sha256: String,
    val downloadUrl: String,
    val compatibleEditions: List<GameEditionKey> = listOf(
        GameEditionKey.PUBG_GLOBAL,
        GameEditionKey.PUBG_KOREA,
        GameEditionKey.PUBG_VIETNAM,
        GameEditionKey.PUBG_TAIWAN
    ),
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isDraft: Boolean = false,
    val downloadProgress: Float = 0f
)
