package com.example.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

const val DEFAULT_PUFFER_TEMP_PATH =
    "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp"

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

data class AppSettings(
    val activeDestinationPath: String = DEFAULT_PUFFER_TEMP_PATH,
    val activeDestinationTreeUri: String? = null,
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
    val message: String
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
