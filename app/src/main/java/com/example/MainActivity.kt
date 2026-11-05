package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.OperationType
import com.example.data.GameEditionKey
import com.example.data.PRESET_GAME_EDITIONS
import com.example.ui.WormCopierViewModel
import com.example.ui.components.SplashScreenOverlay
import com.example.ui.components.TelegramGateDialog
import com.example.ui.components.TransferConfirmationDialog
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.ContentCenterScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PathsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSurface
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WormTempCopierTheme
import kotlinx.coroutines.delay

enum class AppDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", Icons.Filled.Home, Icons.Outlined.Home),
    FILES("files", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    PATHS("paths", Icons.Filled.Route, Icons.Outlined.Route),
    CONTENT("content", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    AUTO_DELETE("autodelete", Icons.Filled.HourglassBottom, Icons.Outlined.HourglassBottom),
    HISTORY("history", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WormCopierApp()
        }
    }
}

@Composable
fun WormCopierApp(
    viewModel: WormCopierViewModel = viewModel(),
    skipSplashForTests: Boolean = false
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val fileSearchQuery by viewModel.fileSearchQuery.collectAsStateWithLifecycle()
    val fileSortOption by viewModel.fileSortOption.collectAsStateWithLifecycle()
    val savedPaths by viewModel.savedPaths.collectAsStateWithLifecycle()
    val favoritePaths by viewModel.favoritePaths.collectAsStateWithLifecycle()
    val historyList by viewModel.operationHistory.collectAsStateWithLifecycle()
    val errorLogs by viewModel.errorLogs.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val isArabic = settings.languageCode == "ar"
    val appStrings = if (isArabic) ArabicStrings else EnglishStrings

    var showSplash by rememberSaveable { mutableStateOf(!skipSplashForTests) }
    var currentTab by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    val scheduledDeletions by viewModel.scheduledDeletions.collectAsStateWithLifecycle()
    val gameEditionStatuses by viewModel.gameEditionStatuses.collectAsStateWithLifecycle()
    val telegramGateCompleted by viewModel.telegramGateCompleted.collectAsStateWithLifecycle()
    val contentItems by viewModel.contentItems.collectAsStateWithLifecycle()
    val downloadingItemId by viewModel.downloadingItemId.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    val activeEditionKey = remember(settings.selectedGameEdition) {
        try {
            GameEditionKey.valueOf(settings.selectedGameEdition)
        } catch (_: Exception) {
            GameEditionKey.PUBG_GLOBAL
        }
    }
    val activeEditionTitle = remember(activeEditionKey) {
        PRESET_GAME_EDITIONS.find { it.key == activeEditionKey }?.title ?: "PUBG MOBILE"
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or dismissed */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(1100L)
            showSplash = false
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3800L)
            viewModel.dismissBanner()
        }
    }

    // BackHandler for non-Home tabs
    BackHandler(enabled = currentTab != AppDestination.HOME) {
        currentTab = AppDestination.HOME
    }

    // System Document Pickers (Zero-storage-permission SAF contracts)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.onFilesPicked(uris)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onDestinationFolderPicked(treeUri)
        }
    }

    val currentPathAccessStatus = remember(
        settings.activeDestinationPath,
        settings.activeDestinationTreeUri,
        shizukuStatus
    ) {
        viewModel.getPathAccessStatus(
            settings.activeDestinationPath,
            settings.activeDestinationTreeUri
        )
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        WormTempCopierTheme(
            darkTheme = settings.darkMode,
            isRtl = isArabic
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            AppDestination.entries.forEach { dest ->
                                val isSelected = currentTab == dest
                                val labelText = when (dest) {
                                    AppDestination.HOME -> appStrings.navHome
                                    AppDestination.FILES -> appStrings.navFiles
                                    AppDestination.PATHS -> appStrings.navPaths
                                    AppDestination.CONTENT -> appStrings.navContentCenter
                                    AppDestination.AUTO_DELETE -> appStrings.navAutoDelete
                                    AppDestination.HISTORY -> appStrings.navHistory
                                    AppDestination.SETTINGS -> appStrings.navSettings
                                }
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = dest },
                                    modifier = Modifier.testTag("nav_tab_${dest.route}"),
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = labelText
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = labelText,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianDark,
                                        selectedTextColor = NeonGreen,
                                        indicatorColor = NeonGreen,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(220),
                            label = "screen_crossfade"
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> DashboardScreen(
                                    shizukuStatus = shizukuStatus,
                                    selectedFiles = selectedFiles,
                                    settings = settings,
                                    favoritePaths = favoritePaths,
                                    pathAccessStatus = currentPathAccessStatus,
                                    transferState = transferState,
                                    onConnectShizuku = viewModel::connectOrRequestShizukuPermission,
                                    onRefreshShizuku = viewModel::refreshShizuku,
                                    onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    onGenerateTestFiles = viewModel::generateSampleTestFiles,
                                    onClearSelection = viewModel::clearSelectedFiles,
                                    onPickDestinationFolder = { folderPickerLauncher.launch(null) },
                                    onSetManualDestination = { path ->
                                        viewModel.setActiveDestinationPath(path)
                                    },
                                    onResetDefaultPath = viewModel::resetToDefaultDestinationPath,
                                    onSelectGameEdition = viewModel::selectGameEdition,
                                    onToggleAutoDelete = viewModel::setAutoDeleteEnabled,
                                    onSetRetentionMinutes = viewModel::setDefaultRetentionMinutes,
                                    onStartCopy = { viewModel.requestStartTransfer(OperationType.COPY) },
                                    onStartMove = { viewModel.requestStartTransfer(OperationType.MOVE) },
                                    onCancelTransfer = viewModel::cancelActiveTransfer,
                                    onNavigateToFiles = { currentTab = AppDestination.FILES },
                                    onNavigateToSettings = { currentTab = AppDestination.SETTINGS },
                                    onToggleLanguage = viewModel::toggleLanguage
                                )

                                AppDestination.FILES -> FileManagerScreen(
                                    files = selectedFiles,
                                    searchQuery = fileSearchQuery,
                                    sortOption = fileSortOption,
                                    onSearchQueryChange = viewModel::setFileSearchQuery,
                                    onSortOptionChange = viewModel::setFileSortOption,
                                    onToggleFileSelection = viewModel::toggleFileSelection,
                                    onSelectAll = viewModel::selectAllFiles,
                                    onRemoveFile = viewModel::removeFileFromQueue,
                                    onComputeFileHash = viewModel::computeSingleFileSha256,
                                    onPickMoreFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    onGenerateTestFiles = viewModel::generateSampleTestFiles
                                )

                                AppDestination.PATHS -> PathsScreen(
                                    savedPaths = savedPaths,
                                    activeDestinationPath = settings.activeDestinationPath,
                                    gameEditionStatuses = gameEditionStatuses,
                                    shizukuReady = shizukuStatus.isReadyForElevatedOps,
                                    checkPathAccess = viewModel::getPathAccessStatus,
                                    onSelectGameEdition = viewModel::selectGameEdition,
                                    onChangeGameEditionPath = viewModel::updateGameEditionPath,
                                    onSelectActivePath = { saved ->
                                        viewModel.setActiveDestinationPath(
                                            path = saved.path,
                                            treeUriString = saved.treeUriString,
                                            label = saved.name
                                        )
                                    },
                                    onAddCustomPath = viewModel::addCustomPath,
                                    onEditSavedPath = viewModel::updateSavedPath,
                                    onToggleFavorite = viewModel::toggleFavoritePath,
                                    onDeletePath = viewModel::deleteSavedPath
                                )

                                AppDestination.CONTENT -> ContentCenterScreen(
                                    contentItems = contentItems,
                                    currentEditionKey = activeEditionKey,
                                    activeEditionTitle = activeEditionTitle,
                                    downloadingItemId = downloadingItemId,
                                    downloadProgress = downloadProgress,
                                    onUseContentItem = viewModel::useContentItem
                                )

                                AppDestination.AUTO_DELETE -> com.example.ui.screens.AutoDeleteScreen(
                                    settings = settings,
                                    scheduledList = scheduledDeletions,
                                    onToggleAutoDelete = viewModel::setAutoDeleteEnabled,
                                    onSetDefaultRetention = viewModel::setDefaultRetentionMinutes,
                                    onCancelSchedule = viewModel::cancelScheduledDeletion,
                                    onDeleteNow = viewModel::deleteScheduledCopyNow,
                                    onClearFinished = viewModel::clearFinishedScheduledDeletions
                                )

                                AppDestination.HISTORY -> HistoryScreen(
                                    historyList = historyList,
                                    errorLogs = errorLogs,
                                    onClearAll = viewModel::clearHistoryAndLogs
                                )

                                AppDestination.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    shizukuStatus = shizukuStatus,
                                    onAutoShizukuChange = viewModel::setAutoUseShizuku,
                                    onConnectShizuku = viewModel::connectOrRequestShizukuPermission,
                                    onConfirmOverwriteChange = viewModel::setConfirmOverwrite,
                                    onVerifySha256Change = viewModel::setVerifySha256,
                                    onVibrateFailureChange = viewModel::setVibrateOnFailure,
                                    onKeepHistoryChange = viewModel::setKeepHistory,
                                    onLanguageChange = viewModel::setLanguage,
                                    onDarkModeChange = viewModel::setDarkMode,
                                    onResetSettings = {
                                        viewModel.resetAllSettings(appStrings.resetSettingsConfirmMsg)
                                    }
                                )
                            }
                        }

                        // Floating feedback toast banner
                        AnimatedVisibility(
                            visible = bannerMessage != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            bannerMessage?.let { (msg, isError) ->
                                val tint = if (isError) ErrorRed else NeonGreen
                                val bg = if (isError) ErrorRedSurface else NeonGreenSurface
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.dismissBanner() }
                                        .testTag("status_banner"),
                                    color = bg,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, tint),
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Move / Overwrite Confirmation Dialog
                pendingConfirmation?.let { pending ->
                    val selectedCount = selectedFiles.count { it.isSelected }
                    TransferConfirmationDialog(
                        pending = pending,
                        destinationPath = settings.activeDestinationPath,
                        selectedCount = selectedCount,
                        onConfirmOverwriteAll = { viewModel.confirmPendingTransfer(skipExisting = false) },
                        onConfirmSkipExisting = { viewModel.confirmPendingTransfer(skipExisting = true) },
                        onDismiss = viewModel::dismissPendingConfirmation
                    )
                }

                // Animated Opening Splash Screen
                SplashScreenOverlay(visible = showSplash)

                // First Launch Telegram Welcome Gate Dialog
                if (!telegramGateCompleted && !showSplash) {
                    TelegramGateDialog(
                        onContinue = viewModel::completeTelegramGate
                    )
                }
            }
        }
    }
}
