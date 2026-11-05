package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.data.GameEditionInfo
import com.example.data.PRESET_GAME_EDITIONS
import com.example.data.OperationStatus
import com.example.data.OperationType
import com.example.data. PathAccessStatus
import com.example.data.SavedPathEntity
import com.example.data.SelectedFileItem
import com.example.data.TransferProgressState
import com.example.domain.FileOperationEngine
import com.example.shizuku.ShizukuStatus
import com.example.ui.components.FileExtensionBadgeIcon
import com.example.ui.components.GamingCard
import com.example.ui.components.PathAccessStatusBadge
import com.example.ui.components.PulsingStatusDot
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    shizukuStatus: ShizukuStatus,
    selectedFiles: List<SelectedFileItem>,
    settings: AppSettings,
    favoritePaths: List<SavedPathEntity>,
    pathAccessStatus: PathAccessStatus,
    transferState: TransferProgressState,
    onConnectShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onPickFiles: () -> Unit,
    onGenerateTestFiles: () -> Unit,
    onClearSelection: () -> Unit,
    onPickDestinationFolder: () -> Unit,
    onSetManualDestination: (String) -> Unit,
    onResetDefaultPath: () -> Unit,
    onSelectGameEdition: (GameEditionInfo) -> Unit,
    onToggleAutoDelete: (Boolean) -> Unit,
    onSetRetentionMinutes: (Int) -> Unit,
    onStartCopy: () -> Unit,
    onStartMove: () -> Unit,
    onCancelTransfer: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val activeSelectedFiles = remember(selectedFiles) { selectedFiles.filter { it.isSelected } }
    val totalSelectedBytes = remember(activeSelectedFiles) { activeSelectedFiles.sumOf { it.sizeBytes } }

    var showManualPathDialog by remember { mutableStateOf(false) }
    var showFavoritePathsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Bar
        item {
            DashboardTopBar(
                shizukuStatus = shizukuStatus,
                onToggleLanguage = onToggleLanguage,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        // 2. Shizuku Status Card with Hero Visual Banner
        item {
            ShizukuStatusCard(
                shizukuStatus = shizukuStatus,
                onConnectRequest = onConnectShizuku,
                onRefresh = onRefreshShizuku
            )
        }

        // 3. Quick Game Paths Edition Selector Card
        item {
            DashboardGameEditionsSelector(
                selectedEditionKey = settings.selectedGameEdition,
                onSelectEdition = onSelectGameEdition
            )
        }

        // 4. Main Operations / Selected Files Card
        item {
            MainOperationsCard(
                activeSelectedFiles = activeSelectedFiles,
                totalSizeFormatted = FileOperationEngine.formatFileSize(totalSelectedBytes),
                onPickFiles = onPickFiles,
                onGenerateTestFiles = onGenerateTestFiles,
                onClearSelection = onClearSelection,
                onOpenFileManager = onNavigateToFiles
            )
        }

        // 5. Destination Folder Card
        item {
            DestinationCard(
                currentPath = settings.activeDestinationPath,
                accessStatus = pathAccessStatus,
                shizukuReady = shizukuStatus.isReadyForElevatedOps,
                onChangeFolder = onPickDestinationFolder,
                onOpenManualPathDialog = { showManualPathDialog = true },
                onResetDefaultPath = onResetDefaultPath,
                onOpenFavoritesDialog = { showFavoritePathsDialog = true }
            )
        }

        // 6. Auto Delete Quick Retention Configuration
        item {
            DashboardAutoDeleteConfig(
                autoDeleteEnabled = settings.autoDeleteEnabled,
                retentionMinutes = settings.defaultRetentionMinutes,
                onToggleAutoDelete = onToggleAutoDelete,
                onSelectRetention = onSetRetentionMinutes
            )
        }

        // 7. Execution Area (COPY / MOVE / Progress Bar / Per-file Results)
        item {
            ExecutionAreaCard(
                transferState = transferState,
                hasSelectedFiles = activeSelectedFiles.isNotEmpty(),
                onStartCopy = onStartCopy,
                onStartMove = onStartMove,
                onCancelTransfer = onCancelTransfer
            )
        }
    }

    if (showManualPathDialog) {
        var inputPath by remember { mutableStateOf(settings.activeDestinationPath) }
        AlertDialog(
            onDismissRequest = { showManualPathDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = strings.btnManualPath,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.pathValueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputPath,
                        onValueChange = { inputPath = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_path_input"),
                        textStyle = MaterialTheme.typography.labelMedium,
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetManualDestination(inputPath)
                        showManualPathDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Text(text = strings.btnSavePath, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPathDialog = false }) {
                    Text(text = strings.btnDismiss, color = TextSecondary)
                }
            }
        )
    }

    if (showFavoritePathsDialog) {
        AlertDialog(
            onDismissRequest = { showFavoritePathsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = strings.favoritePathsSection,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    favoritePaths.forEach { fav ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSetManualDestination(fav.path)
                                    showFavoritePathsDialog = false
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (fav.path == settings.activeDestinationPath) NeonGreen else CarbonBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = fav.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (fav.path == settings.activeDestinationPath) NeonGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (fav.isDefault) {
                                        Text(
                                            text = "DEFAULT",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = NeonGreen
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fav.path,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavoritePathsDialog = false }) {
                    Text(text = strings.btnDismiss, color = NeonGreen)
                }
            }
        )
    }
}

@Composable
private fun DashboardTopBar(
    shizukuStatus: ShizukuStatus,
    onToggleLanguage: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val strings = LocalAppStrings.current
    val statusColor = when {
        shizukuStatus.isReadyForElevatedOps -> NeonGreen
        shizukuStatus.isBinderAlive -> WarningOrange
        else -> ErrorRed
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFF15191D),
                border = BorderStroke(1.5.dp, NeonGreen.copy(alpha = 0.7f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_launcher_icon_1793784567802),
                    contentDescription = "Worm Temp Copier Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        color = NeonGreenSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "v1.2.0",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = if (shizukuStatus.isReadyForElevatedOps) {
                            strings.shizukuConnected
                        } else {
                            strings.shizukuDisconnected
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleLanguage,
                modifier = Modifier.testTag("top_language_toggle_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Switch Language",
                    tint = TacticalCyan
                )
            }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("top_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = strings.navSettings,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    shizukuStatus: ShizukuStatus,
    onConnectRequest: () -> Unit,
    onRefresh: () -> Unit
) {
    val strings = LocalAppStrings.current
    val statusColor = when {
        shizukuStatus.isReadyForElevatedOps -> NeonGreen
        shizukuStatus.isBinderAlive -> WarningOrange
        else -> ErrorRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shizuku_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.55f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle Gaming HUD Banner background at the top
            Image(
                painter = painterResource(id = R.drawable.img_dashboard_banner_1793784568956),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.25f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PulsingStatusDot(color = statusColor)
                        Column {
                            Text(
                                text = strings.shizukuStatusTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (shizukuStatus.isInstalled) {
                                    strings.shizukuInstalledLabel
                                } else {
                                    strings.shizukuNotInstalledLabel
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("shizuku_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.btnRefreshShizuku,
                            tint = NeonGreen
                        )
                    }
                }

                // Dual Status Readout Pills (Connected/Disconnected + Permission Granted/Not Granted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusReadoutChip(
                        label = if (shizukuStatus.isBinderAlive) {
                            strings.shizukuConnected
                        } else {
                            strings.shizukuDisconnected
                        },
                        isPositive = shizukuStatus.isBinderAlive,
                        modifier = Modifier.weight(1f)
                    )
                    StatusReadoutChip(
                        label = if (shizukuStatus.isPermissionGranted) {
                            strings.permissionGranted
                        } else {
                            strings.permissionNotGranted
                        },
                        isPositive = shizukuStatus.isPermissionGranted,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = when {
                        shizukuStatus.isReadyForElevatedOps -> strings.shizukuServiceReadyDesc
                        shizukuStatus.isBinderAlive -> strings.shizukuNeedPermissionDesc
                        else -> strings.shizukuDisconnectedDesc
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onConnectRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("shizuku_connect_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (shizukuStatus.isReadyForElevatedOps) NeonGreenSurface else NeonGreen,
                        contentColor = if (shizukuStatus.isReadyForElevatedOps) NeonGreen else ObsidianDark
                    ),
                    border = if (shizukuStatus.isReadyForElevatedOps) {
                        BorderStroke(1.dp, NeonGreen)
                    } else null
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.btnConnectRequestPermission,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusReadoutChip(
    label: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isPositive) NeonGreen else WarningOrange
    val bg = if (isPositive) NeonGreenSurface else Color(0xFF231B12)
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MainOperationsCard(
    activeSelectedFiles: List<SelectedFileItem>,
    totalSizeFormatted: String,
    onPickFiles: () -> Unit,
    onGenerateTestFiles: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenFileManager: () -> Unit
) {
    val strings = LocalAppStrings.current

    GamingCard(
        modifier = Modifier.testTag("main_operations_card"),
        glowBorderColor = if (activeSelectedFiles.isNotEmpty()) NeonGreen.copy(alpha = 0.4f) else CarbonBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.mainOperationsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (activeSelectedFiles.isNotEmpty()) {
                TextButton(
                    onClick = onClearSelection,
                    modifier = Modifier.testTag("clear_selection_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.btnClearSelection,
                        color = ErrorRed,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Select Files Button
        Button(
            onClick = onPickFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("select_files_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                contentColor = ObsidianDark
            )
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = strings.btnSelectFiles,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick helper button to generate sample .pak / .cfg files for immediate testing
        OutlinedButton(
            onClick = onGenerateTestFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("generate_test_files_btn"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = TacticalCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.btnCreateTestFiles,
                color = TacticalCyan,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Metrics Row: Selected Files Count & Total Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricBox(
                title = strings.selectedFilesCountLabel,
                value = "${activeSelectedFiles.size}",
                accentColor = NeonGreen,
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                title = strings.totalSizeLabel,
                value = totalSizeFormatted,
                accentColor = TacticalCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSelectedFiles.isEmpty()) {
            Text(
                text = strings.noFilesSelectedHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeSelectedFiles.take(4).forEach { fileItem ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenFileManager() },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                FileExtensionBadgeIcon(
                                    extension = fileItem.extension,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column {
                                    Text(
                                        text = fileItem.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ".${fileItem.extension.uppercase()} • ${FileOperationEngine.formatFileSize(fileItem.sizeBytes)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                if (activeSelectedFiles.size > 4) {
                    Text(
                        text = "+${activeSelectedFiles.size - 4} more files...",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = NeonGreen,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onOpenFileManager() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestinationCard(
    currentPath: String,
    accessStatus: PathAccessStatus,
    shizukuReady: Boolean,
    onChangeFolder: () -> Unit,
    onOpenManualPathDialog: () -> Unit,
    onResetDefaultPath: () -> Unit,
    onOpenFavoritesDialog: () -> Unit
) {
    val strings = LocalAppStrings.current

    GamingCard(modifier = Modifier.testTag("destination_card")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = strings.destinationFolderTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = strings.currentDestinationLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Full Monospace Path Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("current_destination_path_box"),
            color = Color(0xFF0E1215),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = currentPath,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PathAccessStatusBadge(
                    status = accessStatus,
                    shizukuReady = shizukuReady
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onChangeFolder,
                modifier = Modifier.testTag("select_destination_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NeonGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnChangeFolder, color = NeonGreen)
            }

            OutlinedButton(
                onClick = onOpenManualPathDialog,
                modifier = Modifier.testTag("manual_path_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TacticalCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.EditLocationAlt,
                    contentDescription = null,
                    tint = TacticalCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnManualPath, color = TacticalCyan)
            }

            OutlinedButton(
                onClick = onResetDefaultPath,
                modifier = Modifier.testTag("default_path_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CarbonBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnDefaultPath, color = TextPrimary)
            }

            OutlinedButton(
                onClick = onOpenFavoritesDialog,
                modifier = Modifier.testTag("favorite_paths_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnFavoritePaths, color = WarningOrange)
            }
        }
    }
}

@Composable
private fun ExecutionAreaCard(
    transferState: TransferProgressState,
    hasSelectedFiles: Boolean,
    onStartCopy: () -> Unit,
    onStartMove: () -> Unit,
    onCancelTransfer: () -> Unit
) {
    val strings = LocalAppStrings.current
    val animatedProgress by animateFloatAsState(
        targetValue = transferState.overallProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280),
        label = "transfer_progress"
    )
    val percentageInt = (animatedProgress * 100).toInt().coerceIn(0, 100)

    GamingCard(
        modifier = Modifier.testTag("execution_area_card"),
        glowBorderColor = if (transferState.isRunning) NeonGreen else CarbonBorder
    ) {
        Text(
            text = strings.executionTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Large COPY (Neon Green) & MOVE (Tactical Cyan) Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartCopy,
                enabled = !transferState.isRunning && hasSelectedFiles,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("execute_copy_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = ObsidianDark,
                    disabledContainerColor = Color(0xFF1B2420),
                    disabledContentColor = TextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.btnCopy,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onStartMove,
                enabled = !transferState.isRunning && hasSelectedFiles,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("execute_move_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalCyan,
                    contentColor = ObsidianDark,
                    disabledContainerColor = Color(0xFF17222B),
                    disabledContentColor = TextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.btnMove,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Bar + Percentage + Completed Counter
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (transferState.isRunning) {
                            if (transferState.isVerifyingHash) {
                                strings.verifyingSha256Label
                            } else {
                                "${strings.executingOperation} (${transferState.currentFileName})"
                            }
                        } else {
                            transferState.summaryMessage ?: strings.readyToExecute
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (transferState.hasError) ErrorRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percentageInt%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (transferState.hasError) ErrorRed else NeonGreen,
                    trackColor = Color(0xFF0E1216)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.filesCompletedOf(
                            transferState.completedFiles,
                            transferState.totalFiles
                        ),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (transferState.isRunning) {
                        OutlinedButton(
                            onClick = onCancelTransfer,
                            modifier = Modifier.testTag("cancel_transfer_btn"),
                            border = BorderStroke(1.dp, ErrorRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.btnCancel,
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Individual File Results List (إظهار نتيجة كل ملف على حدة)
        AnimatedVisibility(visible = transferState.itemResults.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                transferState.itemResults.forEach { res ->
                    val isOk = res.status == OperationStatus.SUCCESS
                    val statusTint = when (res.status) {
                        OperationStatus.SUCCESS -> NeonGreen
                        OperationStatus.FAILED -> ErrorRed
                        OperationStatus.SKIPPED, OperationStatus.CANCELLED -> WarningOrange
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isOk) NeonGreenSurface else ErrorRedSurface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, statusTint.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = statusTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = res.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = res.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = statusTint
                                    )
                                }
                            }
                            if (res.sha256Verified) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "SHA-256",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = NeonGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardGameEditionsSelector(
    selectedEditionKey: String,
    onSelectEdition: (GameEditionInfo) -> Unit
) {
    val strings = LocalAppStrings.current
    GamingCard(glowBorderColor = NeonGreen.copy(alpha = 0.35f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = strings.gamePathsSectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.gamePathsSectionSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRESET_GAME_EDITIONS.forEach { edition ->
                val isSelected = selectedEditionKey == edition.key.name
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectEdition(edition) },
                    label = {
                        Text(
                            text = edition.title.removePrefix("PUBG MOBILE "),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreenSurface,
                        selectedLabelColor = NeonGreen
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = NeonGreen
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardAutoDeleteConfig(
    autoDeleteEnabled: Boolean,
    retentionMinutes: Int,
    onToggleAutoDelete: (Boolean) -> Unit,
    onSelectRetention: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    GamingCard(glowBorderColor = if (autoDeleteEnabled) NeonGreen.copy(alpha = 0.4f) else CarbonBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.autoDeleteSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.autoDeleteToggleDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoDeleteEnabled,
                onCheckedChange = onToggleAutoDelete,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianDark,
                    checkedTrackColor = NeonGreen
                )
            )
        }

        if (autoDeleteEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.retentionDurationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = strings.minuteUnit(retentionMinutes),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..10).forEach { mins ->
                    val isSelected = retentionMinutes == mins
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectRetention(mins) },
                        label = {
                            Text(
                                text = "${mins}m",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreenSurface,
                            selectedLabelColor = NeonGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = NeonGreen
                        )
                    )
                }
            }
        }
    }
}

