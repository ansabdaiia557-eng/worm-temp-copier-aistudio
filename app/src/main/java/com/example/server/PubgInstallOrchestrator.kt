package com.example.server

import android.content.Context
import android.content.Intent
import com.example.content.ContentRepository
import com.example.data.AppSettings
import com.example.data.ContentItem
import com.example.data.GameEditionInfo
import com.example.data.GameEditionKey
import com.example.data.OperationHistoryEntity
import com.example.data.OperationStatus
import com.example.data.OperationType
import com.example.data.PRESET_GAME_EDITIONS
import com.example.data.SelectedFileItem
import com.example.domain.FileOperationEngine
import com.example.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PubgInstallOrchestrator(
    private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val sessionStore: SessionStore,
    private val contentRepository: ContentRepository,
    private val fileEngine: FileOperationEngine,
    private val shizukuManager: ShizukuManager
) {

    data class OrchestrationResult(
        val gateStatus: ServerGateStatus,
        val historyEntries: List<OperationHistoryEntity>,
        val installedEdition: GameEditionInfo?,
        val message: String
    )

    suspend fun runTenStepAutoInstall(
        settings: AppSettings,
        specificItems: List<ContentItem>? = null,
        onStateUpdate: (PubgCopyInstallState) -> Unit
    ): Result<OrchestrationResult> = withContext(Dispatchers.IO) {
        val isAr = settings.languageCode == "ar"
        val historyEntries = mutableListOf<OperationHistoryEntity>()

        fun emitStep(
            step: Int,
            titleAr: String,
            titleEn: String,
            progress: Float,
            doneCount: Int = 0,
            totalCount: Int = 0
        ) {
            onStateUpdate(
                PubgCopyInstallState(
                    isRunning = true,
                    currentStepIndex = step,
                    totalSteps = 10,
                    stepTitle = if (isAr) titleAr else titleEn,
                    progress = progress.coerceIn(0f, 1f),
                    completedFileCount = doneCount,
                    totalFileCount = totalCount,
                    hasError = false,
                    errorMessage = null,
                    canLaunchPubg = false
                )
            )
        }

        try {
            // STEP 1: Connect & synchronize authoritative server gate status
            emitStep(
                step = 1,
                titleAr = "1/10 الاتصال بخادم ANS ومزامنة الحالة...",
                titleEn = "1/10 Connecting to ANS server & syncing gate...",
                progress = 0.08f
            )
            val installedMap = sessionStore.getInstalledFiles()
            val gate = supabaseClient.fetchServerGateStatus(installedMap)
            if (!gate.isReachable) {
                val err = if (isAr) gate.blockReasonAr ?: "تعذر الاتصال بالسيرفر" else gate.blockReasonEn ?: "Server unreachable"
                return@withContext failStep(1, err, onStateUpdate)
            }
            contentRepository.syncCatalogFromServer(gate)

            // STEP 2: Validate user authentication, ban status & active subscription
            emitStep(
                step = 2,
                titleAr = "2/10 التحقق من الحساب والاشتراك...",
                titleEn = "2/10 Verifying account & active subscription...",
                progress = 0.16f
            )
            if (gate.maintenance.isActive) {
                val msg = if (isAr) {
                    gate.maintenance.reasonAr ?: gate.blockReasonAr ?: "السيرفر في وضع الصيانة حالياً."
                } else {
                    gate.maintenance.reasonEn ?: gate.blockReasonEn ?: "Server maintenance is active."
                }
                return@withContext failStep(2, msg, onStateUpdate)
            }
            if (gate.user == null || gate.user.isBanned || (gate.subscription.required && !gate.subscription.isActive)) {
                val msg = if (isAr) {
                    gate.blockReasonAr ?: "يجب تسجيل الدخول وتفعيل اشتراك ساري أولاً."
                } else {
                    gate.blockReasonEn ?: "Sign in with an active subscription first."
                }
                return@withContext failStep(2, msg, onStateUpdate)
            }

            // STEP 3: Validate Telegram channel membership
            emitStep(
                step = 3,
                titleAr = "3/10 التحقق من الاشتراك في قناة تيليجرام...",
                titleEn = "3/10 Verifying Telegram channel membership...",
                progress = 0.24f
            )
            if (gate.telegram.required && !gate.telegram.verified) {
                val msg = if (isAr) {
                    gate.blockReasonAr ?: "يجب التحقق من الانضمام لقناة ${gate.telegram.channelUsername}"
                } else {
                    gate.blockReasonEn ?: "Telegram membership verification required (${gate.telegram.channelUsername})"
                }
                return@withContext failStep(3, msg, onStateUpdate)
            }

            // STEP 4: Validate mandatory app update
            emitStep(
                step = 4,
                titleAr = "4/10 فحص تحديث التطبيق الإلزامي...",
                titleEn = "4/10 Checking mandatory app updates...",
                progress = 0.32f
            )
            if (gate.appUpdate?.isMandatory == true) {
                val msg = if (isAr) {
                    gate.blockReasonAr ?: "يتوفر تحديث إلزامي للتطبيق (${gate.appUpdate.versionName})."
                } else {
                    gate.blockReasonEn ?: "Mandatory app update required (${gate.appUpdate.versionName})."
                }
                return@withContext failStep(4, msg, onStateUpdate)
            }

            // STEP 5: Validate Shizuku / SAF permissions & PUBG edition target path
            emitStep(
                step = 5,
                titleAr = "5/10 فحص مسار PUBG وصلاحيات Shizuku / SAF...",
                titleEn = "5/10 Checking PUBG path & Shizuku / SAF permissions...",
                progress = 0.40f
            )
            val editionKey = try {
                GameEditionKey.valueOf(settings.selectedGameEdition)
            } catch (_: Throwable) {
                GameEditionKey.PUBG_GLOBAL
            }
            val selectedEdition = PRESET_GAME_EDITIONS.find { it.key == editionKey }
                ?: PRESET_GAME_EDITIONS.first()
            // Files always go to the selected edition's own puffer_temp folder.
            val baseDestPath = selectedEdition.defaultPufferPath.trim().trimEnd('/')
            val destTreeUri: String? = null

            val shizukuStatus = shizukuManager.refreshStatus()
            val isRestricted = fileEngine.isRestrictedAndroidDataPath(baseDestPath)
            val hasSafTree = !destTreeUri.isNullOrBlank()
            if (isRestricted && !shizukuStatus.isReadyForElevatedOps && !hasSafTree) {
                val msg = if (isAr) {
                    "يتطلب مسار ${selectedEdition.title} تشغيل Shizuku ومنح الصلاحية أو اختيار المجلد عبر SAF."
                } else {
                    "${selectedEdition.title} path requires active Shizuku permission or SAF folder access."
                }
                return@withContext failStep(5, msg, onStateUpdate)
            }

            // Determine server files to install for this PUBG edition
            val snapshotItems = getItemsSnapshot()
            val itemsToInstall = (specificItems ?: snapshotItems.filter { item ->
                item.compatibleEditions.contains(editionKey) && item.isRequired
            }).distinctBy { it.id }

            if (itemsToInstall.isEmpty()) {
                val msg = if (isAr) {
                    "لا توجد ملفات سيرفر منشورة متوافقة مع ${selectedEdition.title} حالياً."
                } else {
                    "No published server files are currently targeted for ${selectedEdition.title}."
                }
                return@withContext failStep(6, msg, onStateUpdate)
            }

            val totalCount = itemsToInstall.size

            // STEP 6 & 7: Download & verify SHA-256 + byte length of every server package
            emitStep(
                step = 6,
                titleAr = "6/10 تنزيل ملفات السيرفر عبر روابط مؤقتة...",
                titleEn = "6/10 Downloading packages via signed server URLs...",
                progress = 0.48f,
                doneCount = 0,
                totalCount = totalCount
            )
            val downloadedFiles = mutableListOf<Pair<ContentItem, File>>()
            itemsToInstall.forEachIndexed { idx, item ->
                emitStep(
                    step = 6,
                    titleAr = "6/10 تنزيل ${item.targetFileName} (${idx + 1}/$totalCount)...",
                    titleEn = "6/10 Downloading ${item.targetFileName} (${idx + 1}/$totalCount)...",
                    progress = 0.48f + (0.14f * (idx.toFloat() / totalCount)),
                    doneCount = idx,
                    totalCount = totalCount
                )
                val dlRes = contentRepository.downloadVerifiedItem(item) { dlProg ->
                    onStateUpdate(
                        PubgCopyInstallState(
                            isRunning = true,
                            currentStepIndex = 6,
                            totalSteps = 10,
                            stepTitle = if (isAr) {
                                "6/10 تنزيل ${item.targetFileName} (${(dlProg * 100).toInt()}%)"
                            } else {
                                "6/10 Downloading ${item.targetFileName} (${(dlProg * 100).toInt()}%)"
                            },
                            progress = (0.48f + (0.14f * ((idx + dlProg) / totalCount))).coerceIn(0f, 0.99f),
                            completedFileCount = idx,
                            totalFileCount = totalCount
                        )
                    )
                }
                val cachedFile = dlRes.getOrElse { err ->
                    val msg = if (isAr) {
                        "فشل تنزيل ${item.targetFileName}: ${err.message}"
                    } else {
                        "Failed to download ${item.targetFileName}: ${err.message}"
                    }
                    return@withContext failStep(6, msg, onStateUpdate)
                }
                downloadedFiles.add(item to cachedFile)
            }

            // STEP 7: Double-check SHA-256 integrity of cached packages
            emitStep(
                step = 7,
                titleAr = "7/10 التحقق من بصمات SHA-256 وسلامة الحزم...",
                titleEn = "7/10 Verifying SHA-256 checksums of packages...",
                progress = 0.68f,
                doneCount = 0,
                totalCount = totalCount
            )
            val verifiedHashes = mutableMapOf<String, String>()
            for ((item, file) in downloadedFiles) {
                val hash = FileOperationEngine.computeSha256ForFile(file)
                    ?: return@withContext failStep(7, "Failed to hash ${item.targetFileName}", onStateUpdate)
                if (item.sha256.isNotBlank() && !hash.equals(item.sha256, ignoreCase = true)) {
                    return@withContext failStep(
                        7,
                        if (isAr) "بصمة SHA-256 غير مطابقة للملف ${item.targetFileName}" else "SHA-256 mismatch for ${item.targetFileName}",
                        onStateUpdate
                    )
                }
                verifiedHashes[item.id] = hash
            }

            // STEP 8: Prepare destination puffer_temp directory structure
            emitStep(
                step = 8,
                titleAr = "8/10 تجهيز مجلد الهدف (${selectedEdition.title})...",
                titleEn = "8/10 Preparing destination (${selectedEdition.title})...",
                progress = 0.76f,
                doneCount = 0,
                totalCount = totalCount
            )
            if (isRestricted && shizukuStatus.isReadyForElevatedOps) {
                val dirReady = shizukuManager.ensureDirectoryExistsViaShizuku(baseDestPath)
                if (!dirReady) {
                    return@withContext failStep(
                        8,
                        if (isAr) "تعذر تجهيز مجلد puffer_temp عبر Shizuku" else "Unable to prepare puffer_temp via Shizuku",
                        onStateUpdate
                    )
                }
            }

            // STEP 9: Copy files permanently to PUBG target path (NO Auto-Delete scheduling!)
            var completedCopyCount = 0
            for ((idx, pair) in downloadedFiles.withIndex()) {
                val (item, cachedFile) = pair
                val relativeDir = sanitizeRelativeSubPath(item.targetRelativePath)
                val effectiveDestDir = if (relativeDir.isNotBlank()) {
                    "$baseDestPath/$relativeDir"
                } else {
                    baseDestPath
                }

                emitStep(
                    step = 9,
                    titleAr = "9/10 تثبيت ${item.targetFileName} في مجلد اللعبة (${idx + 1}/$totalCount)...",
                    titleEn = "9/10 Installing ${item.targetFileName} into game directory (${idx + 1}/$totalCount)...",
                    progress = 0.80f + (0.14f * (idx.toFloat() / totalCount)),
                    doneCount = idx,
                    totalCount = totalCount
                )

                val selectedItem = SelectedFileItem(
                    id = "srv_${item.id}_${UUID.randomUUID()}",
                    name = item.targetFileName,
                    extension = cachedFile.extension.ifBlank { "pak" },
                    sizeBytes = cachedFile.length(),
                    lastModified = cachedFile.lastModified(),
                    localFilePath = cachedFile.absolutePath,
                    isSelected = true,
                    computedSha256 = verifiedHashes[item.id]
                )

                val (transferRes, usedShizuku) = fileEngine.transferSingleFile(
                    item = selectedItem,
                    destinationPath = effectiveDestDir,
                    destinationTreeUri = if (relativeDir.isBlank()) destTreeUri else null,
                    operationType = OperationType.COPY,
                    verifySha256 = true,
                    autoUseShizuku = settings.autoUseShizuku,
                    skipIfExists = false,
                    onVerifyingHash = {}
                )

                historyEntries.add(
                    OperationHistoryEntity(
                        fileName = item.targetFileName,
                        sourcePath = "ANS Server (${item.version})",
                        destinationPath = "$effectiveDestDir/${item.targetFileName}",
                        operationType = OperationType.COPY.name,
                        timestamp = System.currentTimeMillis(),
                        fileSize = cachedFile.length(),
                        status = transferRes.status.name,
                        sha256Original = transferRes.originalHash,
                        sha256Copied = transferRes.targetHash,
                        sha256Verified = transferRes.sha256Verified,
                        usedShizuku = usedShizuku,
                        errorMessage = if (transferRes.status == OperationStatus.FAILED) transferRes.message else null
                    )
                )

                if (transferRes.status != OperationStatus.SUCCESS) {
                    return@withContext failStep(
                        9,
                        if (isAr) "فشل نسخ ${item.targetFileName}: ${transferRes.message}" else "Copy failed for ${item.targetFileName}: ${transferRes.message}",
                        onStateUpdate
                    )
                }

                // Update installed destination path in SessionStore
                val existingMeta = sessionStore.getInstalledFiles()[item.id]
                if (existingMeta != null) {
                    sessionStore.saveInstalledFile(
                        existingMeta.copy(
                            installedDestPath = transferRes.targetFullPath ?: "$effectiveDestDir/${item.targetFileName}",
                            installedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                completedCopyCount++
            }

            // STEP 10: Record server audit & unlock Launch PUBG button
            emitStep(
                step = 10,
                titleAr = "10/10 توثيق العملية وتفعيل زر تشغيل PUBG...",
                titleEn = "10/10 Recording audit & unlocking Launch PUBG...",
                progress = 0.96f,
                doneCount = completedCopyCount,
                totalCount = totalCount
            )
            supabaseClient.recordOperationAudit(
                action = "PUBG_AUTO_INSTALL",
                status = "success",
                details = mapOf(
                    "edition" to selectedEdition.key.name,
                    "package_name" to selectedEdition.packageName,
                    "files_count" to completedCopyCount,
                    "global_file_revision" to gate.globalFileRevision,
                    "file_ids" to downloadedFiles.map { it.first.id }
                )
            )

            sessionStore.markPubgInstallVerified(
                editionKey = selectedEdition.key.name,
                globalRevision = gate.globalFileRevision
            )

            val doneMsg = if (isAr) {
                "اكتمل تثبيت $completedCopyCount ملفات في ${selectedEdition.title} بنجاح! يمكنك تشغيل اللعبة الآن."
            } else {
                "Installed $completedCopyCount files to ${selectedEdition.title} successfully! Ready to launch PUBG."
            }

            onStateUpdate(
                PubgCopyInstallState(
                    isRunning = false,
                    currentStepIndex = 10,
                    totalSteps = 10,
                    stepTitle = doneMsg,
                    progress = 1.0f,
                    completedFileCount = completedCopyCount,
                    totalFileCount = totalCount,
                    hasError = false,
                    errorMessage = null,
                    canLaunchPubg = true,
                    lastInstalledEditionTitle = selectedEdition.title
                )
            )

            Result.success(
                OrchestrationResult(
                    gateStatus = gate,
                    historyEntries = historyEntries,
                    installedEdition = selectedEdition,
                    message = doneMsg
                )
            )
        } catch (t: Throwable) {
            failStep(0, t.localizedMessage ?: "Unexpected installation error", onStateUpdate)
        }
    }

    private suspend fun getItemsSnapshot(): List<ContentItem> {
        return contentRepository.itemsFlow.firstOrNull().orEmpty()
    }

    private suspend fun failStep(
        step: Int,
        message: String,
        onStateUpdate: (PubgCopyInstallState) -> Unit
    ): Result<OrchestrationResult> {
        sessionStore.clearPubgInstallVerified()
        onStateUpdate(
            PubgCopyInstallState(
                isRunning = false,
                currentStepIndex = step,
                totalSteps = 10,
                stepTitle = message,
                progress = 0f,
                hasError = true,
                errorMessage = message,
                canLaunchPubg = false
            )
        )
        return Result.failure(IllegalStateException(message))
    }

    fun launchPubgPackage(packageName: String): Result<Unit> {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return Result.failure(IllegalStateException("PUBG package ($packageName) is not installed on this device."))
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun sanitizeRelativeSubPath(raw: String): String {
        return raw.trim()
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
    }
}
