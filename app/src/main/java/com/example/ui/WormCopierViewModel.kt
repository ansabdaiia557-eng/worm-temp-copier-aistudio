package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.data.ErrorLogEntity
import com.example.data.FileSortOption
import com.example.data.FileTransferItemResult
import com.example.data.GamePathAccessState
import com.example.data.OperationHistoryEntity
import com.example.data.OperationStatus
import com.example.data.OperationType
import com.example.data.PathAccessStatus
import com.example.data.PRESET_GAME_EDITIONS
import com.example.data.SavedPathEntity
import com.example.data.ScheduledDeletionEntity
import com.example.data.SelectedFileItem
import com.example.data.SettingsRepository
import com.example.data.TransferProgressState
import com.example.domain.FileOperationEngine
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingTransferConfirmation(
    val operationType: OperationType,
    val needsMoveConfirmation: Boolean = false,
    val conflictingFileNames: List<String> = emptyList()
)

class WormCopierViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val savedPathDao = database.savedPathDao()
    private val historyDao = database.operationHistoryDao()
    private val errorLogDao = database.errorLogDao()
    private val scheduledDeletionDao = database.scheduledDeletionDao()

    val settingsRepository = SettingsRepository(application)
    val shizukuManager = ShizukuManager(application)
    val fileEngine = FileOperationEngine(application, shizukuManager)

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuManager.status

    val savedPaths: StateFlow<List<SavedPathEntity>> = savedPathDao.getAllPaths().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoritePaths: StateFlow<List<SavedPathEntity>> = savedPathDao.getFavoritePaths().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val operationHistory: StateFlow<List<OperationHistoryEntity>> = historyDao.getAllHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val errorLogs: StateFlow<List<ErrorLogEntity>> = errorLogDao.getAllLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scheduledDeletions: StateFlow<List<ScheduledDeletionEntity>> = scheduledDeletionDao.getPendingDeletions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _gameEditionStatuses = MutableStateFlow<Map<String, GamePathAccessState>>(emptyMap())
    val gameEditionStatuses: StateFlow<Map<String, GamePathAccessState>> = _gameEditionStatuses.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFileItem>> = _selectedFiles.asStateFlow()

    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery: StateFlow<String> = _fileSearchQuery.asStateFlow()

    private val _fileSortOption = MutableStateFlow(FileSortOption.NAME)
    val fileSortOption: StateFlow<FileSortOption> = _fileSortOption.asStateFlow()

    private val _transferState = MutableStateFlow(TransferProgressState())
    val transferState: StateFlow<TransferProgressState> = _transferState.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingTransferConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingTransferConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _bannerMessage = MutableStateFlow<Pair<String, Boolean>?>(null) // message to isError
    val bannerMessage: StateFlow<Pair<String, Boolean>?> = _bannerMessage.asStateFlow()

    private var activeTransferJob: Job? = null

    init {
        shizukuManager.registerListeners()
        viewModelScope.launch {
            seedDefaultPathsIfEmpty()
            // Populate initial sample test files so user can immediately inspect & test
            val sampleFiles = fileEngine.createSampleTestFiles()
            _selectedFiles.value = sampleFiles
            refreshGameEditionStatuses()
        }
    }

    fun refreshGameEditionStatuses() {
        viewModelScope.launch {
            val map = mutableMapOf<String, GamePathAccessState>()
            for (edition in PRESET_GAME_EDITIONS) {
                val state = fileEngine.checkGamePathAccessStatus(
                    path = edition.defaultPufferPath,
                    packageName = edition.packageName
                )
                map[edition.packageName] = state
            }
            _gameEditionStatuses.value = map
        }
    }

    override fun onCleared() {
        super.onCleared()
        shizukuManager.unregisterListeners()
    }

    private suspend fun seedDefaultPathsIfEmpty() {
        if (savedPathDao.getCount() == 0) {
            savedPathDao.insertPath(
                SavedPathEntity(
                    name = "Puffer Temp (Default UE4)",
                    path = DEFAULT_PUFFER_TEMP_PATH,
                    isFavorite = true,
                    isDefault = true,
                    usageCount = 1
                )
            )
            val sandboxPath = fileEngine.getLocalTestDestinationPath()
            savedPathDao.insertPath(
                SavedPathEntity(
                    name = "Puffer Sandbox (مجلد اختبار مباشر)",
                    path = sandboxPath,
                    isFavorite = true,
                    isDefault = false,
                    usageCount = 1
                )
            )
            savedPathDao.insertPath(
                SavedPathEntity(
                    name = "Download / WormOutput",
                    path = "/storage/emulated/0/Download/WormTempCopier",
                    isFavorite = false,
                    isDefault = false,
                    usageCount = 0
                )
            )
        }
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _bannerMessage.value = message to isError
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    // --- Shizuku Actions ---
    fun refreshShizuku() {
        val status = shizukuManager.refreshStatus()
        showBanner(status.statusDetailMessage, isError = !status.isBinderAlive)
    }

    fun connectOrRequestShizukuPermission() {
        val msg = shizukuManager.connectOrRequestPermission()
        showBanner(msg, isError = !shizukuStatus.value.isBinderAlive)
    }

    // --- File Selection & Management ---
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = uris.mapNotNull { uri ->
                fileEngine.resolveUriToSelectedFile(uri)
            }
            if (resolved.isNotEmpty()) {
                _selectedFiles.update { existing ->
                    val existingIds = existing.map { it.id }.toSet()
                    existing + resolved.filter { it.id !in existingIds }
                }
                showBanner(
                    if (settings.value.languageCode == "ar") {
                        "تمت إضافة ${resolved.size} ملفات إلى القائمة"
                    } else {
                        "Added ${resolved.size} files to queue"
                    }
                )
            }
        }
    }

    fun generateSampleTestFiles() {
        viewModelScope.launch {
            val generated = fileEngine.createSampleTestFiles()
            _selectedFiles.update { existing ->
                val map = existing.associateBy { it.id }.toMutableMap()
                generated.forEach { map[it.id] = it }
                map.values.toList()
            }
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "تم توليد ${generated.size} ملفات اختبار (.pak / .cfg / .json) جاهزة للنسخ!"
                } else {
                    "Generated ${generated.size} test files (.pak / .cfg / .json) ready for transfer!"
                }
            )
        }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun toggleFileSelection(fileId: String) {
        _selectedFiles.update { list ->
            list.map { if (it.id == fileId) it.copy(isSelected = !it.isSelected) else it }
        }
    }

    fun selectAllFiles(selectAll: Boolean) {
        _selectedFiles.update { list ->
            list.map { it.copy(isSelected = selectAll) }
        }
    }

    fun removeFileFromQueue(fileId: String) {
        _selectedFiles.update { list ->
            list.filterNot { it.id == fileId }
        }
    }

    fun setFileSearchQuery(query: String) {
        _fileSearchQuery.value = query
    }

    fun setFileSortOption(option: FileSortOption) {
        _fileSortOption.value = option
    }

    fun computeSingleFileSha256(fileId: String) {
        viewModelScope.launch {
            val target = _selectedFiles.value.find { it.id == fileId } ?: return@launch
            val hash = fileEngine.computeSha256ForItem(target)
            if (hash != null) {
                _selectedFiles.update { list ->
                    list.map { if (it.id == fileId) it.copy(computedSha256 = hash) else it }
                }
            } else {
                logError("SHA-256", "Failed to compute SHA-256 for ${target.name}", target.displayPath)
            }
        }
    }

    // --- Destination & Paths Management ---
    fun onDestinationFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }

            val doc = DocumentFile.fromTreeUri(ctx, treeUri)
            val folderName = doc?.name ?: "Selected_Folder"
            val readablePath = resolveTreeUriDisplayPath(treeUri, folderName)

            settingsRepository.setActiveDestination(
                path = readablePath,
                treeUriString = treeUri.toString()
            )
            saveOrUpdateRecentPath(
                name = folderName,
                path = readablePath,
                treeUriString = treeUri.toString()
            )
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "تم تحديد مجلد الوجهة: $folderName"
                } else {
                    "Destination set to: $folderName"
                }
            )
        }
    }

    private fun resolveTreeUriDisplayPath(treeUri: Uri, fallbackName: String): String {
        val raw = treeUri.lastPathSegment ?: return "/storage/emulated/0/$fallbackName"
        return if (raw.startsWith("primary:")) {
            "/storage/emulated/0/" + raw.removePrefix("primary:")
        } else {
            "/storage/emulated/0/$fallbackName"
        }
    }

    fun setActiveDestinationPath(path: String, treeUriString: String? = null, label: String? = null) {
        val cleanPath = path.trim()
        if (cleanPath.isEmpty() || !cleanPath.startsWith("/")) {
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "يرجى إدخال مسار صحيح يبدأ بـ /"
                } else {
                    "Please enter a valid absolute path starting with /"
                },
                isError = true
            )
            return
        }
        viewModelScope.launch {
            settingsRepository.setActiveDestination(cleanPath, treeUriString)
            saveOrUpdateRecentPath(
                name = label ?: cleanPath.substringAfterLast('/').ifBlank { "Custom Path" },
                path = cleanPath,
                treeUriString = treeUriString
            )
        }
    }

    fun resetToDefaultDestinationPath() {
        viewModelScope.launch {
            settingsRepository.setActiveDestination(DEFAULT_PUFFER_TEMP_PATH, null)
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "تمت استعادة مسار puffer_temp الافتراضي"
                } else {
                    "Restored default puffer_temp path"
                }
            )
        }
    }

    fun getPathAccessStatus(path: String, treeUriString: String? = null): PathAccessStatus {
        return fileEngine.checkPathAccessStatus(path, treeUriString)
    }

    private suspend fun saveOrUpdateRecentPath(
        name: String,
        path: String,
        treeUriString: String? = null
    ) {
        val existing = savedPathDao.getPathByValue(path)
        if (existing != null) {
            savedPathDao.updatePath(
                existing.copy(
                    lastUsedAt = System.currentTimeMillis(),
                    usageCount = existing.usageCount + 1,
                    treeUriString = treeUriString ?: existing.treeUriString
                )
            )
        } else {
            savedPathDao.insertPath(
                SavedPathEntity(
                    name = name,
                    path = path,
                    treeUriString = treeUriString,
                    isFavorite = false,
                    isDefault = path == DEFAULT_PUFFER_TEMP_PATH,
                    lastUsedAt = System.currentTimeMillis(),
                    usageCount = 1
                )
            )
        }
    }

    fun addCustomPath(name: String, path: String, isFavorite: Boolean, setAsActive: Boolean) {
        val cleanPath = path.trim()
        val cleanName = name.trim().ifEmpty { cleanPath.substringAfterLast('/').ifEmpty { "Custom Path" } }
        if (!cleanPath.startsWith("/")) {
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "المسار غير صالح: يجب أن يبدأ بـ /"
                } else {
                    "Invalid path: Must start with /"
                },
                isError = true
            )
            return
        }
        viewModelScope.launch {
            val existing = savedPathDao.getPathByValue(cleanPath)
            if (existing != null) {
                savedPathDao.updatePath(
                    existing.copy(
                        name = cleanName,
                        isFavorite = isFavorite,
                        lastUsedAt = System.currentTimeMillis()
                    )
                )
            } else {
                savedPathDao.insertPath(
                    SavedPathEntity(
                        name = cleanName,
                        path = cleanPath,
                        isFavorite = isFavorite,
                        isDefault = cleanPath == DEFAULT_PUFFER_TEMP_PATH
                    )
                )
            }
            if (setAsActive) {
                settingsRepository.setActiveDestination(cleanPath, null)
            }
        }
    }

    fun updateSavedPath(entity: SavedPathEntity, newName: String, newPath: String) {
        val cleanPath = newPath.trim()
        if (!cleanPath.startsWith("/")) return
        viewModelScope.launch {
            savedPathDao.updatePath(
                entity.copy(
                    name = newName.trim().ifEmpty { entity.name },
                    path = cleanPath
                )
            )
        }
    }

    fun toggleFavoritePath(entity: SavedPathEntity) {
        viewModelScope.launch {
            savedPathDao.updatePath(entity.copy(isFavorite = !entity.isFavorite))
        }
    }

    fun deleteSavedPath(entity: SavedPathEntity) {
        if (entity.isDefault) return
        viewModelScope.launch {
            savedPathDao.deletePath(entity)
        }
    }

    // --- COPY & MOVE Execution Pipeline ---
    fun requestStartTransfer(operationType: OperationType) {
        val activeFiles = _selectedFiles.value.filter { it.isSelected }
        if (activeFiles.isEmpty()) {
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "يرجى اختيار ملف واحد على الأقل قبل بدء العملية"
                } else {
                    "Please select at least one file before starting"
                },
                isError = true
            )
            return
        }

        val currentSettings = settings.value
        val destPath = currentSettings.activeDestinationPath.trim()
        if (destPath.isEmpty() || !destPath.startsWith("/")) {
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "مسار الوجهة غير صالح"
                } else {
                    "Destination path is invalid"
                },
                isError = true
            )
            return
        }

        viewModelScope.launch {
            val conflicts = if (currentSettings.confirmOverwrite) {
                fileEngine.findExistingDestinationFiles(
                    files = activeFiles,
                    destinationPath = destPath,
                    destinationTreeUri = currentSettings.activeDestinationTreeUri,
                    useShizuku = currentSettings.autoUseShizuku
                )
            } else emptyList()

            if (operationType == OperationType.MOVE || conflicts.isNotEmpty()) {
                _pendingConfirmation.value = PendingTransferConfirmation(
                    operationType = operationType,
                    needsMoveConfirmation = operationType == OperationType.MOVE,
                    conflictingFileNames = conflicts
                )
            } else {
                executeTransferBatch(operationType = operationType, skipExisting = false)
            }
        }
    }

    fun confirmPendingTransfer(skipExisting: Boolean) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        executeTransferBatch(
            operationType = pending.operationType,
            skipExisting = skipExisting
        )
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    private fun executeTransferBatch(
        operationType: OperationType,
        skipExisting: Boolean
    ) {
        if (_transferState.value.isRunning) return

        val filesToProcess = _selectedFiles.value.filter { it.isSelected }
        if (filesToProcess.isEmpty()) return

        activeTransferJob = viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            val destPath = currentSettings.activeDestinationPath.trim()
            val destTreeUri = currentSettings.activeDestinationTreeUri

            // 1. Check available storage space before starting
            val requiredBytes = filesToProcess.sumOf { it.sizeBytes }
            val availableBytes = fileEngine.getAvailableSpaceBytes(destPath)
            if (requiredBytes > availableBytes) {
                val msg = if (currentSettings.languageCode == "ar") {
                    "المساحة المتاحة غير كافية في الوجهة (${FileOperationEngine.formatFileSize(availableBytes)})"
                } else {
                    "Insufficient free disk space at destination (${FileOperationEngine.formatFileSize(availableBytes)})"
                }
                logError("DISK_SPACE", msg, "Required: $requiredBytes, Available: $availableBytes")
                triggerFailureVibrationIfEnabled(currentSettings.vibrateOnFailure)
                showBanner(msg, isError = true)
                return@launch
            }

            // Record recent destination path usage
            saveOrUpdateRecentPath(
                name = destPath.substringAfterLast('/').ifEmpty { "Destination" },
                path = destPath,
                treeUriString = destTreeUri
            )

            val total = filesToProcess.size
            val results = mutableListOf<FileTransferItemResult>()
            val successfullyMovedIds = mutableSetOf<String>()

            _transferState.value = TransferProgressState(
                isRunning = true,
                operationType = operationType,
                currentFileName = filesToProcess.first().name,
                completedFiles = 0,
                totalFiles = total,
                overallProgress = 0.02f,
                isVerifyingHash = false,
                itemResults = emptyList(),
                summaryMessage = null,
                hasError = false
            )

            try {
                for ((index, item) in filesToProcess.withIndex()) {
                    _transferState.update {
                        it.copy(
                            currentFileName = item.name,
                            completedFiles = index,
                            overallProgress = (index.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f),
                            isVerifyingHash = false
                        )
                    }

                    val (itemResult, usedShizuku) = try {
                        fileEngine.transferSingleFile(
                            item = item,
                            destinationPath = destPath,
                            destinationTreeUri = destTreeUri,
                            operationType = operationType,
                            verifySha256 = currentSettings.verifySha256,
                            autoUseShizuku = currentSettings.autoUseShizuku,
                            skipIfExists = skipExisting,
                            onVerifyingHash = {
                                _transferState.update { state -> state.copy(isVerifyingHash = true) }
                            }
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        val errMsg = t.localizedMessage ?: "Unexpected file I/O error"
                        logError("TRANSFER_${operationType.name}", errMsg, "${item.displayPath} -> $destPath")
                        FileTransferItemResult(
                            fileName = item.name,
                            fileSize = item.sizeBytes,
                            status = OperationStatus.FAILED,
                            sha256Verified = false,
                            originalHash = null,
                            targetHash = null,
                            message = errMsg
                        ) to false
                    }

                    results.add(itemResult)

                    if (itemResult.status == OperationStatus.FAILED) {
                        logError(
                            tag = "${operationType.name}_FAIL",
                            message = "${item.name}: ${itemResult.message}",
                            details = "Src: ${item.displayPath} -> Dst: $destPath"
                        )
                    }

                    if (operationType == OperationType.MOVE && itemResult.status == OperationStatus.SUCCESS) {
                        successfullyMovedIds.add(item.id)
                    }

                    // Auto-Delete Scheduling: starts countdown only after successful COPY
                    if (operationType == OperationType.COPY && itemResult.status == OperationStatus.SUCCESS && currentSettings.autoDeleteEnabled) {
                        val copyId = itemResult.uniqueCopyId ?: "copy_${System.currentTimeMillis()}"
                        val targetPath = itemResult.targetFullPath ?: "$destPath/${item.name}"
                        val retentionMinutes = currentSettings.defaultRetentionMinutes.coerceIn(1, 10)
                        val now = System.currentTimeMillis()
                        val delayMillis = retentionMinutes * 60 * 1000L
                        val deleteAt = now + delayMillis

                        val scheduledEntity = ScheduledDeletionEntity(
                            uniqueCopyId = copyId,
                            fileName = item.name,
                            destinationPath = targetPath,
                            fileSize = item.sizeBytes,
                            expectedSha256 = itemResult.targetHash ?: itemResult.originalHash,
                            gameEditionName = PRESET_GAME_EDITIONS.find { it.key.name == currentSettings.selectedGameEdition }?.title ?: "PUBG MOBILE",
                            durationMinutes = retentionMinutes,
                            copiedAtMillis = now,
                            deleteAtMillis = deleteAt,
                            isDeleted = false,
                            cancelled = false
                        )
                        scheduledDeletionDao.insert(scheduledEntity)
                        com.example.autodelete.AutoDeleteWorker.scheduleDeletion(
                            context = getApplication(),
                            uniqueCopyId = copyId,
                            delayMillis = delayMillis
                        )
                    }

                    if (currentSettings.keepHistory) {
                        historyDao.insertHistory(
                            OperationHistoryEntity(
                                fileName = item.name,
                                sourcePath = item.displayPath,
                                destinationPath = "$destPath/${item.name}",
                                operationType = operationType.name,
                                timestamp = System.currentTimeMillis(),
                                fileSize = item.sizeBytes,
                                status = itemResult.status.name,
                                sha256Original = itemResult.originalHash,
                                sha256Copied = itemResult.targetHash,
                                sha256Verified = itemResult.sha256Verified,
                                usedShizuku = usedShizuku,
                                errorMessage = if (itemResult.status == OperationStatus.FAILED) itemResult.message else null
                            )
                        )
                    }

                    val doneCount = index + 1
                    _transferState.update {
                        it.copy(
                            completedFiles = doneCount,
                            overallProgress = doneCount.toFloat() / total.toFloat(),
                            isVerifyingHash = false,
                            itemResults = results.toList()
                        )
                    }
                }

                // If MOVE succeeded for some files, update selected files list to reflect moved files
                if (successfullyMovedIds.isNotEmpty()) {
                    _selectedFiles.update { list ->
                        list.filterNot { it.id in successfullyMovedIds }
                    }
                }

                val successCount = results.count { it.status == OperationStatus.SUCCESS }
                val failCount = results.count { it.status == OperationStatus.FAILED }
                val hasAnyError = failCount > 0

                if (hasAnyError) {
                    triggerFailureVibrationIfEnabled(currentSettings.vibrateOnFailure)
                }

                val summary = if (currentSettings.languageCode == "ar") {
                    "اكتملت العملية (${operationType.name}): نجح $successCount من $total ملفات" +
                        if (failCount > 0) " • فشل $failCount" else ""
                } else {
                    "${operationType.name} finished: $successCount/$total succeeded" +
                        if (failCount > 0) " • $failCount failed" else ""
                }

                _transferState.update {
                    it.copy(
                        isRunning = false,
                        completedFiles = total,
                        overallProgress = 1f,
                        isVerifyingHash = false,
                        itemResults = results.toList(),
                        summaryMessage = summary,
                        hasError = hasAnyError
                    )
                }
                showBanner(summary, isError = hasAnyError)
            } catch (_: CancellationException) {
                val cancelMsg = if (currentSettings.languageCode == "ar") {
                    "تم إلغاء العملية بأمان دون المساس بالملفات الأصلية"
                } else {
                    "Operation safely cancelled; original files preserved"
                }
                if (currentSettings.keepHistory) {
                    historyDao.insertHistory(
                        OperationHistoryEntity(
                            fileName = _transferState.value.currentFileName.ifEmpty { "Batch" },
                            sourcePath = "Batch Selection",
                            destinationPath = destPath,
                            operationType = operationType.name,
                            timestamp = System.currentTimeMillis(),
                            fileSize = 0L,
                            status = OperationStatus.CANCELLED.name,
                            sha256Verified = false,
                            errorMessage = cancelMsg
                        )
                    )
                }
                _transferState.update {
                    it.copy(
                        isRunning = false,
                        isVerifyingHash = false,
                        summaryMessage = cancelMsg,
                        hasError = false
                    )
                }
                showBanner(cancelMsg, isError = false)
            }
        }
    }

    fun cancelActiveTransfer() {
        activeTransferJob?.cancel()
        activeTransferJob = null
    }

    // --- History & Error Logs ---
    fun clearHistoryAndLogs() {
        viewModelScope.launch {
            historyDao.clearAllHistory()
            errorLogDao.clearAllLogs()
        }
    }

    private suspend fun logError(tag: String, message: String, details: String? = null) {
        errorLogDao.insertLog(
            ErrorLogEntity(
                timestamp = System.currentTimeMillis(),
                tag = tag,
                message = message,
                details = details
            )
        )
    }

    private fun triggerFailureVibrationIfEnabled(enabled: Boolean) {
        if (!enabled) return
        try {
            val ctx = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(90L)
                }
            }
        } catch (_: Throwable) {
        }
    }

    // --- Settings Updates ---
    fun setAutoUseShizuku(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUseShizuku(enabled) }
    }

    fun setConfirmOverwrite(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmOverwrite(enabled) }
    }

    fun setVerifySha256(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVerifySha256(enabled) }
    }

    fun setKeepHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepHistory(enabled) }
    }

    fun setVibrateOnFailure(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrateOnFailure(enabled) }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (settings.value.languageCode == "ar") "en" else "ar"
            settingsRepository.setLanguageCode(next)
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { settingsRepository.setLanguageCode(code) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun selectGameEdition(edition: com.example.data.GameEditionInfo) {
        viewModelScope.launch {
            settingsRepository.setSelectedGameEdition(edition.key.name)
            settingsRepository.setActiveDestination(edition.defaultPufferPath, null)
            saveOrUpdateRecentPath(
                name = edition.title,
                path = edition.defaultPufferPath,
                treeUriString = null
            )
            refreshGameEditionStatuses()
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "تم اختيار ${edition.title} وتعيين المسار تلقائياً"
                } else {
                    "Selected ${edition.title} and set destination"
                }
            )
        }
    }

    fun updateGameEditionPath(edition: com.example.data.GameEditionInfo, newPath: String) {
        val clean = newPath.trim()
        if (!clean.startsWith("/")) return
        viewModelScope.launch {
            settingsRepository.setActiveDestination(clean, null)
            saveOrUpdateRecentPath(name = edition.title, path = clean, treeUriString = null)
            refreshGameEditionStatuses()
        }
    }

    fun setAutoDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDeleteEnabled(enabled) }
    }

    fun setDefaultRetentionMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultRetentionMinutes(minutes) }
    }

    fun cancelScheduledDeletion(uniqueCopyId: String) {
        viewModelScope.launch {
            scheduledDeletionDao.cancelSchedule(uniqueCopyId)
            com.example.autodelete.AutoDeleteWorker.cancelScheduledWork(getApplication(), uniqueCopyId)
            showBanner(
                if (settings.value.languageCode == "ar") {
                    "تم إلغاء الجدولة • لن يتم حذف النسخة تلقائياً"
                } else {
                    "Scheduled deletion cancelled"
                }
            )
        }
    }

    fun deleteScheduledCopyNow(entity: com.example.data.ScheduledDeletionEntity) {
        viewModelScope.launch {
            com.example.autodelete.AutoDeleteWorker.cancelScheduledWork(getApplication(), entity.uniqueCopyId)
            val (success, reason) = fileEngine.safeDeleteTemporaryCopy(
                targetFilePath = entity.destinationPath,
                expectedSha256 = entity.expectedSha256
            )
            val notifId = entity.uniqueCopyId.hashCode()
            if (success) {
                scheduledDeletionDao.markDeleted(entity.uniqueCopyId)
                com.example.autodelete.DeletionNotificationHelper.notifyDeletionSuccess(
                    context = getApplication(),
                    notificationId = notifId,
                    fileName = entity.fileName,
                    gameEditionName = entity.gameEditionName,
                    retentionMinutes = entity.durationMinutes
                )
                showBanner(
                    if (settings.value.languageCode == "ar") {
                        "تم حذف النسخة المؤقتة بنجاح (${entity.fileName})"
                    } else {
                        "Temporary copy deleted successfully (${entity.fileName})"
                    }
                )
            } else {
                val err = reason ?: "Failed to delete"
                scheduledDeletionDao.markFailed(entity.uniqueCopyId, err)
                com.example.autodelete.DeletionNotificationHelper.notifyDeletionFailure(
                    context = getApplication(),
                    notificationId = notifId,
                    fileName = entity.fileName,
                    reason = err
                )
                showBanner(err, isError = true)
            }
        }
    }

    fun clearFinishedScheduledDeletions() {
        viewModelScope.launch { scheduledDeletionDao.clearFinished() }
    }

    fun resetAllSettings(confirmMsg: String) {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            showBanner(confirmMsg, isError = false)
        }
    }
}
