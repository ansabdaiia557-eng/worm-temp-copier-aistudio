package com.example.content

import android.content.Context
import com.example.data.ContentCategory
import com.example.data.ContentItem
import com.example.data.GameEditionKey
import com.example.domain.FileOperationEngine
import com.example.server.InstalledServerFileMeta
import com.example.server.ServerContentFileDto
import com.example.server.ServerGateStatus
import com.example.server.SessionStore
import com.example.server.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ContentRepository(
    private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val sessionStore: SessionStore
) {
    private val contentCacheDir = File(context.filesDir, "ans_server_cache").apply { mkdirs() }

    private val _itemsFlow = MutableStateFlow<List<ContentItem>>(emptyList())
    val itemsFlow: Flow<List<ContentItem>> = _itemsFlow.asStateFlow()

    suspend fun syncCatalogFromServer(gateStatus: ServerGateStatus) = withContext(Dispatchers.IO) {
        val installedMap = sessionStore.getInstalledFiles()
        val outdatedSet = gateStatus.outdatedFileIds.toSet()

        val mapped = gateStatus.files.map { dto ->
            mapDtoToContentItem(dto, installedMap[dto.id], outdatedSet)
        }
        _itemsFlow.value = mapped
    }

    suspend fun refreshLocalCacheState() = withContext(Dispatchers.IO) {
        val installedMap = sessionStore.getInstalledFiles()
        _itemsFlow.value = _itemsFlow.value.map { item ->
            val meta = installedMap[item.id]
            val localFile = meta?.localCachePath?.let { File(it) }
            val hasFile = localFile != null && localFile.exists() && localFile.length() > 0L
            val isLatest = hasFile &&
                meta != null &&
                meta.versionCode >= item.versionCode &&
                (item.sha256.isBlank() || meta.sha256.equals(item.sha256, ignoreCase = true))

            item.copy(
                isDownloaded = hasFile && isLatest,
                localFilePath = if (hasFile) localFile.absolutePath else null,
                installedVersionCode = meta?.versionCode ?: 0,
                needsUpdate = !isLatest && (meta != null || item.isRequired)
            )
        }
    }

    suspend fun downloadOrPrepareItem(
        itemId: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val item = _itemsFlow.value.find { it.id == itemId }
            ?: return@withContext Result.failure(IllegalArgumentException("Server content item not found: $itemId"))

        downloadVerifiedItem(item, onProgress)
    }

    suspend fun downloadVerifiedItem(
        item: ContentItem,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val safeFilename = sanitizeFilename(item.targetFileName.ifBlank { "${item.id}.pak" })
            val destinationFile = File(contentCacheDir, "${item.id}_$safeFilename")
            val installedMeta = sessionStore.getInstalledFiles()[item.id]

            // Reuse verified cached copy ONLY if version_code AND sha256 both match server record
            if (destinationFile.exists() &&
                destinationFile.length() > 0L &&
                installedMeta != null &&
                installedMeta.versionCode >= item.versionCode &&
                !item.needsUpdate
            ) {
                val actualHash = FileOperationEngine.computeSha256ForFile(destinationFile)
                val sizeOk = item.sizeBytes <= 0L || destinationFile.length() == item.sizeBytes
                val hashOk = item.sha256.isBlank() || (actualHash != null && actualHash.equals(item.sha256, ignoreCase = true))
                if (sizeOk && hashOk) {
                    onProgress(1f)
                    return@withContext Result.success(destinationFile)
                }
            }

            // Request fresh 1-hour signed download URL from Edge Function
            onProgress(0.05f)
            val signedResult = supabaseClient.requestSignedDownloadUrl(item.id)
            val payload = signedResult.getOrElse { err ->
                return@withContext Result.failure(err)
            }

            val dlResult = supabaseClient.downloadAndVerifyBinary(
                signedUrl = payload.signedUrl,
                destinationFile = destinationFile,
                expectedSha256 = payload.sha256.ifBlank { item.sha256 },
                expectedSizeBytes = if (payload.sizeBytes > 0L) payload.sizeBytes else item.sizeBytes,
                onProgress = onProgress
            )

            val verifiedSha = dlResult.getOrElse { err ->
                return@withContext Result.failure(err)
            }

            val updatedMeta = InstalledServerFileMeta(
                contentId = item.id,
                versionCode = payload.versionCode,
                versionName = payload.version,
                sha256 = verifiedSha,
                localCachePath = destinationFile.absolutePath,
                installedDestPath = installedMeta?.installedDestPath ?: "",
                installedAtMillis = System.currentTimeMillis(),
                sizeBytes = destinationFile.length()
            )
            sessionStore.saveInstalledFile(updatedMeta)

            _itemsFlow.value = _itemsFlow.value.map { current ->
                if (current.id == item.id) {
                    current.copy(
                        isDownloaded = true,
                        localFilePath = destinationFile.absolutePath,
                        sizeBytes = destinationFile.length(),
                        sha256 = verifiedSha,
                        versionCode = payload.versionCode,
                        version = payload.version,
                        installedVersionCode = payload.versionCode,
                        needsUpdate = false
                    )
                } else current
            }

            Result.success(destinationFile)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun mapDtoToContentItem(
        dto: ServerContentFileDto,
        installed: InstalledServerFileMeta?,
        outdatedIds: Set<String>
    ): ContentItem {
        val category = when (dto.category.uppercase(Locale.ROOT)) {
            "SKINS" -> ContentCategory.SKINS
            "AIM_SETTINGS" -> ContentCategory.AIM_SETTINGS
            else -> ContentCategory.ALL_FEATURES
        }
        val editions = dto.compatibleEditions.mapNotNull { keyName ->
            try {
                GameEditionKey.valueOf(keyName)
            } catch (_: Throwable) {
                null
            }
        }.ifEmpty { GameEditionKey.entries }

        val cachedFile = installed?.localCachePath?.takeIf { it.isNotBlank() }?.let { File(it) }
        val fileExists = cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L
        val hashMatches = installed != null &&
            (dto.sha256.isBlank() || installed.sha256.equals(dto.sha256, ignoreCase = true))
        val versionUpToDate = installed != null && installed.versionCode >= dto.versionCode
        val isLatestLocal = fileExists && hashMatches && versionUpToDate && dto.id !in outdatedIds

        return ContentItem(
            id = dto.id,
            title = dto.title,
            description = dto.description,
            category = category,
            version = dto.version,
            sizeBytes = dto.sizeBytes,
            updatedAt = dto.updatedAt,
            targetFileName = dto.targetFilename,
            sha256 = dto.sha256,
            storagePath = dto.storagePath,
            compatibleEditions = editions,
            isDownloaded = isLatestLocal,
            localFilePath = if (fileExists) cachedFile!!.absolutePath else null,
            isDraft = false,
            versionCode = dto.versionCode,
            targetRelativePath = dto.targetRelativePath,
            isRequired = dto.isRequired,
            targetGroup = dto.targetGroup,
            installedVersionCode = installed?.versionCode ?: 0,
            needsUpdate = !isLatestLocal && (installed != null || dto.id in outdatedIds)
        )
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    fun isCompatibleWith(item: ContentItem, currentEdition: GameEditionKey): Boolean {
        return item.compatibleEditions.contains(currentEdition)
    }
}
