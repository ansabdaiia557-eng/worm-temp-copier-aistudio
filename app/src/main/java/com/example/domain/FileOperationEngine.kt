package com.example.domain

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.data.FileTransferItemResult
import com.example.data.GamePathAccessState
import com.example.data.OperationStatus
import com.example.data.OperationType
import com.example.data.PathAccessStatus
import com.example.data.SelectedFileItem
import com.example.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.pow

class FileOperationEngine(
    private val context: Context,
    private val shizukuManager: ShizukuManager
) {

    /**
     * Resolves metadata for a content:// or file:// Uri selected via Android's document picker.
     */
    suspend fun resolveUriToSelectedFile(uri: Uri): SelectedFileItem? = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return@withContext null)
                if (!file.exists()) return@withContext null
                val ext = file.extension.lowercase(Locale.ROOT)
                return@withContext SelectedFileItem(
                    id = file.absolutePath,
                    name = file.name,
                    extension = ext.ifEmpty { "file" },
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    uri = uri,
                    localFilePath = file.absolutePath,
                    isSelected = true
                )
            }

            var displayName = "unknown_file"
            var sizeBytes = 0L
            var lastModified = System.currentTimeMillis()

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            val docFile = DocumentFile.fromSingleUri(context, uri)
            if (docFile != null) {
                if (displayName == "unknown_file" && !docFile.name.isNullOrBlank()) {
                    displayName = docFile.name!!
                }
                if (sizeBytes <= 0L) {
                    sizeBytes = docFile.length()
                }
                if (docFile.lastModified() > 0L) {
                    lastModified = docFile.lastModified()
                }
            }

            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            SelectedFileItem(
                id = uri.toString(),
                name = displayName,
                extension = ext.ifEmpty { "bin" },
                sizeBytes = sizeBytes,
                lastModified = lastModified,
                uri = uri,
                localFilePath = null,
                isSelected = true
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates realistic sample gaming utility files (.pak, .cfg, .dat) in a local source sandbox
     * so users can immediately test real COPY, MOVE, SHA-256 verification, and overwrite confirmation.
     */
    suspend fun createSampleTestFiles(): List<SelectedFileItem> = withContext(Dispatchers.IO) {
        val sourceSandbox = File(context.getExternalFilesDir(null) ?: context.filesDir, "worm_source_workspace")
        if (!sourceSandbox.exists()) {
            sourceSandbox.mkdirs()
        }

        val specs = listOf(
            Triple(
                "game_patch_3.4.0_puffer.pak",
                "pak",
                buildString {
                    repeat(600) { idx ->
                        append("WORM_PAK_BLOCK_#${idx}_UE4_SHADOWTRACKER_ASSET_PAYLOAD_9F8E7D6C5B4A\n")
                    }
                }
            ),
            Triple(
                "core_shader_cache_v12.pak",
                "pak",
                buildString {
                    repeat(420) { idx ->
                        append("SHADER_PIPELINE_CACHE_ENTRY_${idx}_NEON_UTILITY_DATA_STREAM\n")
                    }
                }
            ),
            Triple(
                "UserCustomDeviceProfile.cfg",
                "cfg",
                """
                [ScalabilityGroups]
                sg.ResolutionQuality=100.000000
                sg.ViewDistanceQuality=3
                sg.AntiAliasingQuality=3
                sg.ShadowQuality=2
                r.PUBGMaxSupportQualityLevel=4
                r.UserHDRSetting=1
                """.trimIndent()
            ),
            Triple(
                "puffer_manifest_hash.json",
                "json",
                """
                {
                  "engine": "UE4-ShadowTrackerExtra",
                  "channel": "puffer_temp",
                  "version": "1.2.0",
                  "verified": true
                }
                """.trimIndent()
            )
        )

        val createdItems = mutableListOf<SelectedFileItem>()
        for ((fileName, ext, content) in specs) {
            val file = File(sourceSandbox, fileName)
            file.writeText(content)
            createdItems.add(
                SelectedFileItem(
                    id = file.absolutePath,
                    name = file.name,
                    extension = ext,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    uri = Uri.fromFile(file),
                    localFilePath = file.absolutePath,
                    isSelected = true
                )
            )
        }
        createdItems
    }

    fun getLocalTestDestinationPath(): String {
        val dir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "puffer_temp_sandbox"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    /**
     * Evaluates whether a path is directly writable, can be created, or requires Shizuku.
     */
    fun checkPathAccessStatus(path: String, treeUriString: String? = null): PathAccessStatus {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("/")) {
            return PathAccessStatus.INVALID
        }

        if (!treeUriString.isNullOrBlank()) {
            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                if (doc != null && doc.canWrite()) {
                    return PathAccessStatus.DIRECT_WRITABLE
                }
            } catch (_: Throwable) {
            }
        }

        if (isRestrictedAndroidDataPath(trimmed)) {
            return PathAccessStatus.NEEDS_SHIZUKU
        }

        val dir = File(trimmed)
        if (dir.exists() && dir.isDirectory && dir.canWrite()) {
            return PathAccessStatus.DIRECT_WRITABLE
        }

        var parent = dir.parentFile
        while (parent != null && !parent.exists()) {
            parent = parent.parentFile
        }
        if (parent != null && parent.canWrite()) {
            return PathAccessStatus.WILL_CREATE_DIR
        }

        return PathAccessStatus.NEEDS_SHIZUKU
    }

    /**
     * Specifically checks game edition path availability:
     * - Returns NOT_FOUND if app package is not installed or base folder does not exist
     * - Returns PERMISSION_REQUIRED if access needs Shizuku/SAF and it is not granted
     * - Returns AVAILABLE if accessible or writable
     */
    suspend fun checkGamePathAccessStatus(path: String, packageName: String): GamePathAccessState = withContext(Dispatchers.IO) {
        val packageInstalled = shizukuManager?.isPackageInstalled(packageName) ?: false
        val fileObj = File(path)

        if (fileObj.exists() && fileObj.canWrite()) {
            return@withContext GamePathAccessState.AVAILABLE
        }

        // For restricted Android/data paths
        if (isRestrictedAndroidDataPath(path)) {
            val status = shizukuManager?.refreshStatus()
            if (status != null && status.isReadyForElevatedOps) {
                val exists = shizukuManager.directoryExistsViaShizuku(path)
                return@withContext if (exists || packageInstalled) {
                    GamePathAccessState.AVAILABLE
                } else {
                    GamePathAccessState.NOT_FOUND
                }
            } else {
                return@withContext if (packageInstalled || isDirectoryProbeFound(path)) {
                    GamePathAccessState.PERMISSION_REQUIRED
                } else {
                    GamePathAccessState.NOT_FOUND
                }
            }
        }

        if (fileObj.exists()) {
            return@withContext GamePathAccessState.AVAILABLE
        }

        val parent = fileObj.parentFile
        if (parent != null && parent.exists() && parent.canWrite()) {
            return@withContext GamePathAccessState.AVAILABLE
        }

        if (packageInstalled) {
            GamePathAccessState.PERMISSION_REQUIRED
        } else {
            GamePathAccessState.NOT_FOUND
        }
    }

    private fun isDirectoryProbeFound(path: String): Boolean {
        var cursor: File? = File(path)
        while (cursor != null) {
            if (cursor.exists()) return true
            cursor = cursor.parentFile
        }
        return false
    }

    /**
     * Safe deletion of a temporary copy:
     * - Verifies that the file is indeed the target copy inside the destination
     * - Verifies the SHA-256 fingerprint if available to prevent deleting unrelated files
     * - Refuses to delete if file has been modified or replaced
     */
    suspend fun safeDeleteTemporaryCopy(
        targetFilePath: String,
        expectedSha256: String?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val target = File(targetFilePath)

        // 1. If accessible directly
        if (target.exists() && target.isFile) {
            return@withContext safeDeleteDirectFile(target, expectedSha256)
        }

        // 2. If in restricted Android/data path, use Shizuku
        if (isRestrictedAndroidDataPath(targetFilePath)) {
            val status = shizukuManager.refreshStatus()
            if (!status.isReadyForElevatedOps) {
                return@withContext false to "Shizuku service/permission required to delete from Android/data"
            }

            val exists = shizukuManager.fileExistsViaShizuku(targetFilePath)
            if (!exists) {
                return@withContext true to null // already gone
            }

            if (!expectedSha256.isNullOrBlank()) {
                val currentHash = shizukuManager.computeSha256ViaShizuku(targetFilePath)
                if (currentHash != null && currentHash != expectedSha256) {
                    return@withContext false to "Target file SHA-256 changed or was modified since copy."
                }
            }

            val res = shizukuManager.deleteFileViaShizuku(targetFilePath)
            return@withContext if (res.isSuccess) {
                true to null
            } else {
                false to (res.stderr.ifBlank { "Shizuku rm failed with code ${res.exitCode}" })
            }
        }

        if (!target.exists()) {
            return@withContext true to null
        }

        false to "Target file not found or inaccessible"
    }

    fun isRestrictedAndroidDataPath(path: String): Boolean {
        return isRestrictedPath(path, context.packageName)
    }

    /**
     * Checks available bytes on the storage volume hosting [destinationPath].
     */
    fun getAvailableSpaceBytes(destinationPath: String): Long {
        return try {
            var probe = File(destinationPath)
            while (!probe.exists() && probe.parentFile != null) {
                probe = probe.parentFile!!
            }
            val target = if (probe.exists()) probe else Environment.getDataDirectory()
            val stat = StatFs(target.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            Long.MAX_VALUE
        }
    }

    /**
     * Computes SHA-256 of a SelectedFileItem (either local file or ContentResolver Uri).
     */
    suspend fun computeSha256ForItem(item: SelectedFileItem): String? = withContext(Dispatchers.IO) {
        try {
            val localPath = item.localFilePath
            if (!localPath.isNullOrBlank()) {
                val file = File(localPath)
                if (file.exists() && file.canRead()) {
                    return@withContext computeSha256FromStream(FileInputStream(file))
                }
            }
            val uri = item.uri ?: return@withContext null
            context.contentResolver.openInputStream(uri)?.use { stream ->
                computeSha256FromStream(stream)
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun computeSha256ForFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) return@withContext null
            FileInputStream(file).use { stream ->
                computeSha256FromStream(stream)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun computeSha256FromStream(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            coroutineContext.ensureActive()
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns names of files from [files] that already exist inside [destinationPath].
     */
    suspend fun findExistingDestinationFiles(
        files: List<SelectedFileItem>,
        destinationPath: String,
        destinationTreeUri: String?,
        useShizuku: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        val existing = mutableListOf<String>()
        val destDir = File(destinationPath)

        val treeDoc = if (!destinationTreeUri.isNullOrBlank()) {
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(destinationTreeUri))
            } catch (_: Throwable) {
                null
            }
        } else null

        for (item in files) {
            coroutineContext.ensureActive()
            if (treeDoc != null && treeDoc.canWrite()) {
                if (treeDoc.findFile(item.name) != null) {
                    existing.add(item.name)
                }
                continue
            }

            val targetFile = File(destDir, item.name)
            if (targetFile.exists()) {
                existing.add(item.name)
            } else if (useShizuku && isRestrictedAndroidDataPath(destinationPath) &&
                shizukuManager.status.value.isReadyForElevatedOps
            ) {
                if (shizukuManager.fileExistsViaShizuku("${destinationPath.trimEnd('/')}/${item.name}")) {
                    existing.add(item.name)
                }
            }
        }
        existing
    }

    /**
     * Executes a single file COPY or MOVE with full safety checks:
     * - Self-copy prevention
     * - Overwrite policy enforcement
     * - Direct I/O, SAF DocumentFile, or Shizuku elevated transfer
     * - SHA-256 original vs destination verification
     * - Safe deletion of original ONLY after verified MOVE
     */
    suspend fun transferSingleFile(
        item: SelectedFileItem,
        destinationPath: String,
        destinationTreeUri: String?,
        operationType: OperationType,
        verifySha256: Boolean,
        autoUseShizuku: Boolean,
        skipIfExists: Boolean,
        onVerifyingHash: () -> Unit
    ): Pair<FileTransferItemResult, Boolean> = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        val normalizedDestDir = destinationPath.trim().trimEnd('/')
        val targetFullPath = "$normalizedDestDir/${item.name}"

        // 1. Prevent copying/moving a file onto itself
        val sourceLocalPath = item.localFilePath
        if (!sourceLocalPath.isNullOrBlank()) {
            val srcCanonical = try {
                File(sourceLocalPath).canonicalPath
            } catch (_: Throwable) {
                File(sourceLocalPath).absolutePath
            }
            val dstCanonical = try {
                File(targetFullPath).canonicalPath
            } catch (_: Throwable) {
                File(targetFullPath).absolutePath
            }
            if (srcCanonical == dstCanonical) {
                return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.FAILED,
                    sha256Verified = false,
                    originalHash = null,
                    targetHash = null,
                    message = "Prevented copying file onto itself ($srcCanonical)"
                ) to false
            }
        }

        // 2. Compute original SHA-256 before transfer if enabled or if MOVE is requested
        val shouldHash = verifySha256 || operationType == OperationType.MOVE
        val originalHash = if (shouldHash) {
            onVerifyingHash()
            item.computedSha256 ?: computeSha256ForItem(item)
        } else null

        // 3. Check if SAF Tree URI is active and writable
        if (!destinationTreeUri.isNullOrBlank()) {
            val treeDoc = try {
                DocumentFile.fromTreeUri(context, Uri.parse(destinationTreeUri))
            } catch (_: Throwable) {
                null
            }
            if (treeDoc != null && treeDoc.canWrite()) {
                val existingDoc = treeDoc.findFile(item.name)
                if (existingDoc != null) {
                    if (skipIfExists) {
                        return@withContext FileTransferItemResult(
                            fileName = item.name,
                            fileSize = item.sizeBytes,
                            status = OperationStatus.SKIPPED,
                            sha256Verified = false,
                            originalHash = originalHash,
                            targetHash = null,
                            message = "Skipped existing file in destination"
                        ) to false
                    }
                    existingDoc.delete()
                }

                val targetDoc = treeDoc.createFile("application/octet-stream", item.name)
                    ?: return@withContext FileTransferItemResult(
                        fileName = item.name,
                        fileSize = item.sizeBytes,
                        status = OperationStatus.FAILED,
                        sha256Verified = false,
                        originalHash = originalHash,
                        targetHash = null,
                        message = "Failed to create file in SAF destination folder"
                    ) to false

                try {
                    openItemInputStream(item)?.use { input ->
                        context.contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                output.write(buf, 0, read)
                            }
                            output.flush()
                        }
                    } ?: return@withContext FileTransferItemResult(
                        fileName = item.name,
                        fileSize = item.sizeBytes,
                        status = OperationStatus.FAILED,
                        sha256Verified = false,
                        originalHash = originalHash,
                        targetHash = null,
                        message = "Could not open source file stream"
                    ) to false

                    val targetHash = if (shouldHash) {
                        onVerifyingHash()
                        context.contentResolver.openInputStream(targetDoc.uri)?.use {
                            computeSha256FromStream(it)
                        }
                    } else null

                    val verified = !shouldHash || (originalHash != null && originalHash == targetHash)
                    if (!verified) {
                        targetDoc.delete()
                        return@withContext FileTransferItemResult(
                            fileName = item.name,
                            fileSize = item.sizeBytes,
                            status = OperationStatus.FAILED,
                            sha256Verified = false,
                            originalHash = originalHash,
                            targetHash = targetHash,
                            message = "SHA-256 checksum mismatch! Target removed for safety."
                        ) to false
                    }

                    if (operationType == OperationType.MOVE) {
                        deleteOriginalSource(item)
                    }

                    val uniqueCopyId = "copy_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
                    return@withContext FileTransferItemResult(
                        fileName = item.name,
                        fileSize = item.sizeBytes,
                        status = OperationStatus.SUCCESS,
                        sha256Verified = verifySha256 && verified,
                        originalHash = originalHash,
                        targetHash = targetHash,
                        message = "${operationType.name} completed via SAF",
                        uniqueCopyId = uniqueCopyId,
                        targetFullPath = targetFullPath
                    ) to false
                } catch (t: Throwable) {
                    targetDoc.delete()
                    throw t
                }
            }
        }

        // 4. Try direct filesystem I/O if not a restricted Android/data path (or if writable)
        val destDir = File(normalizedDestDir)
        val needsShizuku = isRestrictedAndroidDataPath(normalizedDestDir) ||
            (!destDir.exists() && !destDir.mkdirs() && !destDir.canWrite())

        if (!needsShizuku) {
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            val targetFile = File(destDir, item.name)
            if (targetFile.exists() && skipIfExists) {
                return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.SKIPPED,
                    sha256Verified = false,
                    originalHash = originalHash,
                    targetHash = null,
                    message = "Skipped existing file in destination"
                ) to false
            }

            // Copy to temporary .worm_tmp file first so interrupted operations never corrupt existing files
            val tempFile = File(destDir, "${item.name}.worm_tmp")
            try {
                openItemInputStream(item)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buf, 0, read)
                        }
                        output.fd.sync()
                    }
                } ?: return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.FAILED,
                    sha256Verified = false,
                    originalHash = originalHash,
                    targetHash = null,
                    message = "Unable to open source file for reading"
                ) to false

                val targetHash = if (shouldHash) {
                    onVerifyingHash()
                    computeSha256ForFile(tempFile)
                } else null

                val verified = !shouldHash || (originalHash != null && originalHash == targetHash)
                if (!verified) {
                    tempFile.delete()
                    return@withContext FileTransferItemResult(
                        fileName = item.name,
                        fileSize = item.sizeBytes,
                        status = OperationStatus.FAILED,
                        sha256Verified = false,
                        originalHash = originalHash,
                        targetHash = targetHash,
                        message = "SHA-256 verification failed! Original preserved."
                    ) to false
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (operationType == OperationType.MOVE) {
                    deleteOriginalSource(item)
                }

                val uniqueCopyId = "copy_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
                return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.SUCCESS,
                    sha256Verified = verifySha256 && verified,
                    originalHash = originalHash,
                    targetHash = targetHash,
                    message = "${operationType.name} completed and verified",
                    uniqueCopyId = uniqueCopyId,
                    targetFullPath = targetFile.absolutePath
                ) to false
            } catch (t: Throwable) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                throw t
            }
        }

        // 5. Restricted path (such as /storage/emulated/0/Android/data/com.tencent.ig/.../puffer_temp) -> Use Shizuku
        if (!autoUseShizuku) {
            return@withContext FileTransferItemResult(
                fileName = item.name,
                fileSize = item.sizeBytes,
                status = OperationStatus.FAILED,
                sha256Verified = false,
                originalHash = originalHash,
                targetHash = null,
                message = "Destination requires Shizuku, but Auto-Shizuku is disabled in Settings."
            ) to false
        }

        val shizukuStatus = shizukuManager.refreshStatus()
        if (!shizukuStatus.isReadyForElevatedOps) {
            return@withContext FileTransferItemResult(
                fileName = item.name,
                fileSize = item.sizeBytes,
                status = OperationStatus.FAILED,
                sha256Verified = false,
                originalHash = originalHash,
                targetHash = null,
                message = "Restricted path ($normalizedDestDir) requires active Shizuku connection & permission."
            ) to false
        }

        if (skipIfExists && shizukuManager.fileExistsViaShizuku(targetFullPath)) {
            return@withContext FileTransferItemResult(
                fileName = item.name,
                fileSize = item.sizeBytes,
                status = OperationStatus.SKIPPED,
                sha256Verified = false,
                originalHash = originalHash,
                targetHash = null,
                message = "Skipped existing file via Shizuku check"
            ) to true
        }

        // Stage content:// URI to an accessible external cache file if source is not a direct file path
        var tempStageFile: File? = null
        val readableSourcePath = if (!sourceLocalPath.isNullOrBlank() && File(sourceLocalPath).exists()) {
            sourceLocalPath
        } else {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val staged = File(cacheDir, "shizuku_stage_${System.currentTimeMillis()}_${item.name}")
            openItemInputStream(item)?.use { input ->
                FileOutputStream(staged).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buf, 0, read)
                    }
                }
            } ?: return@withContext FileTransferItemResult(
                fileName = item.name,
                fileSize = item.sizeBytes,
                status = OperationStatus.FAILED,
                sha256Verified = false,
                originalHash = originalHash,
                targetHash = null,
                message = "Failed to stage source file for Shizuku transfer"
            ) to true
            tempStageFile = staged
            staged.absolutePath
        }

        try {
            val copyRes = shizukuManager.copyFileViaShizuku(readableSourcePath, targetFullPath)
            if (!copyRes.isSuccess) {
                return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.FAILED,
                    sha256Verified = false,
                    originalHash = originalHash,
                    targetHash = null,
                    message = "Shizuku transfer failed: ${copyRes.stderr.ifBlank { "Exit code ${copyRes.exitCode}" }}"
                ) to true
            }

            val targetHash = if (shouldHash) {
                onVerifyingHash()
                shizukuManager.computeSha256ViaShizuku(targetFullPath)
            } else null

            val verified = !shouldHash || (originalHash != null && originalHash == targetHash)
            if (!verified) {
                return@withContext FileTransferItemResult(
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    status = OperationStatus.FAILED,
                    sha256Verified = false,
                    originalHash = originalHash,
                    targetHash = targetHash,
                    message = "SHA-256 mismatch on Shizuku target! Original file kept safe."
                ) to true
            }

            if (operationType == OperationType.MOVE) {
                deleteOriginalSource(item)
            }

            val uniqueCopyId = "copy_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            return@withContext FileTransferItemResult(
                fileName = item.name,
                fileSize = item.sizeBytes,
                status = OperationStatus.SUCCESS,
                sha256Verified = verifySha256 && verified,
                originalHash = originalHash,
                targetHash = targetHash,
                message = "${operationType.name} completed via Shizuku",
                uniqueCopyId = uniqueCopyId,
                targetFullPath = targetFullPath
            ) to true
        } finally {
            tempStageFile?.delete()
        }
    }

    private fun openItemInputStream(item: SelectedFileItem): InputStream? {
        if (!item.localFilePath.isNullOrBlank()) {
            val f = File(item.localFilePath)
            if (f.exists() && f.canRead()) {
                return FileInputStream(f)
            }
        }
        val uri = item.uri ?: return null
        return context.contentResolver.openInputStream(uri)
    }

    private fun deleteOriginalSource(item: SelectedFileItem): Boolean {
        return try {
            if (!item.localFilePath.isNullOrBlank()) {
                val f = File(item.localFilePath)
                if (f.exists()) return f.delete()
            }
            val uri = item.uri ?: return false
            val doc = DocumentFile.fromSingleUri(context, uri)
            doc?.delete() ?: false
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
            return DecimalFormat("#,##0.##").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
        }

        fun computeSha256ForFile(file: File): String? {
            if (!file.exists() || !file.canRead()) return null
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                file.inputStream().use { stream ->
                    var bytesRead = stream.read(buffer)
                    while (bytesRead != -1) {
                        digest.update(buffer, 0, bytesRead)
                        bytesRead = stream.read(buffer)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (_: Throwable) {
                null
            }
        }

        fun computeSha256FromStream(inputStream: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead = inputStream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun isRestrictedPath(path: String, ownPackage: String = ""): Boolean {
            val normalized = path.replace('\\', '/')
            if (ownPackage.isNotBlank() && (normalized.contains("/Android/data/$ownPackage") || normalized.contains("/Android/obb/$ownPackage"))) {
                return false
            }
            return normalized.contains("/Android/data/") || normalized.contains("/Android/obb/")
        }

        fun safeDeleteDirectFile(target: File, expectedSha256: String?): Pair<Boolean, String?> {
            if (!target.exists()) return true to null
            if (!expectedSha256.isNullOrBlank()) {
                val currentHash = computeSha256ForFile(target)
                if (currentHash != null && currentHash != expectedSha256) {
                    return false to "SHA-256 mismatch: File was modified or replaced after copy!"
                }
            }
            val deleted = target.delete()
            return if (deleted) {
                true to null
            } else {
                false to "Could not delete file from filesystem (Permission Denied)"
            }
        }
    }
}
