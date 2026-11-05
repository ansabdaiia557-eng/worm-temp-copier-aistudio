package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ErrorLogEntity
import com.example.data.OperationHistoryEntity
import com.example.domain.FileOperationEngine
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    historyList: List<OperationHistoryEntity>,
    errorLogs: List<ErrorLogEntity>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var typeFilter by remember { mutableStateOf("ALL") } // ALL, COPY, MOVE
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, SUCCESS, FAILED

    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    val filteredHistory = remember(historyList, typeFilter, statusFilter) {
        historyList.filter { entry ->
            val matchesType = typeFilter == "ALL" || entry.operationType == typeFilter
            val matchesStatus = when (statusFilter) {
                "SUCCESS" -> entry.status == "SUCCESS"
                "FAILED" -> entry.status == "FAILED"
                else -> true
            }
            matchesType && matchesStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("history_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.historyScreenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.historyScreenSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (historyList.isNotEmpty() || errorLogs.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_history_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ErrorRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = strings.btnClearHistory, color = ErrorRed, fontSize = 12.sp)
                }
            }
        }

        // Tabs: Operations Log vs Error & System Logs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = NeonGreen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                modifier = Modifier.testTag("tab_operations_log"),
                text = {
                    Text(
                        text = "${strings.tabOperations} (${historyList.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                modifier = Modifier.testTag("tab_error_logs"),
                text = {
                    Text(
                        text = "${strings.tabErrorLogs} (${errorLogs.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        if (selectedTabIndex == 0) {
            // Filters Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryFilterChip(
                    label = strings.filterAll,
                    selected = typeFilter == "ALL" && statusFilter == "ALL",
                    onClick = {
                        typeFilter = "ALL"
                        statusFilter = "ALL"
                    }
                )
                HistoryFilterChip(
                    label = strings.filterCopy,
                    selected = typeFilter == "COPY",
                    onClick = { typeFilter = if (typeFilter == "COPY") "ALL" else "COPY" }
                )
                HistoryFilterChip(
                    label = strings.filterMove,
                    selected = typeFilter == "MOVE",
                    onClick = { typeFilter = if (typeFilter == "MOVE") "ALL" else "MOVE" }
                )
                HistoryFilterChip(
                    label = strings.filterSuccess,
                    selected = statusFilter == "SUCCESS",
                    onClick = { statusFilter = if (statusFilter == "SUCCESS") "ALL" else "SUCCESS" }
                )
                HistoryFilterChip(
                    label = strings.filterFailed,
                    selected = statusFilter == "FAILED",
                    onClick = { statusFilter = if (statusFilter == "FAILED") "ALL" else "FAILED" }
                )
            }

            if (filteredHistory.isEmpty()) {
                GamingCard(modifier = Modifier.padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(46.dp)
                        )
                        Text(
                            text = strings.emptyHistoryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.emptyHistoryDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredHistory, key = { it.id }) { entry ->
                        val isSuccess = entry.status == "SUCCESS"
                        val isCopy = entry.operationType == "COPY"
                        val statusColor = when (entry.status) {
                            "SUCCESS" -> NeonGreen
                            "FAILED" -> ErrorRed
                            else -> WarningOrange
                        }

                        GamingCard(
                            glowBorderColor = statusColor.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("history_card_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = entry.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = if (isCopy) NeonGreenSurface else TacticalCyanSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCopy) NeonGreen.copy(alpha = 0.4f) else TacticalCyan.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Text(
                                            text = entry.operationType,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCopy) NeonGreen else TacticalCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Surface(
                                        color = if (isSuccess) NeonGreenSurface else ErrorRedSurface,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = entry.status,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = FileOperationEngine.formatFileSize(entry.fileSize),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TacticalCyan
                                )
                                Text(
                                    text = dateFormatter.format(Date(entry.timestamp)),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = Color(0xFF0E1215),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${strings.sourceLabel} ${entry.sourcePath}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${strings.destinationLabel} ${entry.destinationPath}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // SHA-256 & Shizuku Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = if (entry.sha256Verified) NeonGreen else WarningOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (entry.sha256Verified) {
                                            strings.sha256VerifiedBadge
                                        } else {
                                            strings.sha256SkippedBadge
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = if (entry.sha256Verified) NeonGreen else WarningOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (entry.usedShizuku) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = TacticalCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Shizuku IPC",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = TacticalCyan
                                        )
                                    }
                                }
                            }

                            if (!entry.sha256Original.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "SHA-256: ${entry.sha256Original}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!entry.errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = entry.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Error & System Logs Tab
            if (errorLogs.isEmpty()) {
                GamingCard(modifier = Modifier.padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(46.dp)
                        )
                        Text(
                            text = strings.emptyErrorLogsDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonGreen
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(errorLogs, key = { it.id }) { log ->
                        GamingCard(glowBorderColor = ErrorRed.copy(alpha = 0.45f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${log.tag}]",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                                Text(
                                    text = dateFormatter.format(Date(log.timestamp)),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            if (!log.details.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = log.details,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonGreenSurface,
            selectedLabelColor = NeonGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = NeonGreen
        )
    )
}
