package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameEditionInfo
import com.example.data.OperationHistoryEntity
import com.example.data.PRESET_GAME_EDITIONS
import com.example.server.AuthSessionState
import com.example.server.PubgCopyInstallState
import com.example.server.ServerGateStatus
import com.example.shizuku.ShizukuStatus
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
fun HomeScreen(
    shizukuStatus: ShizukuStatus,
    selectedEditionKey: String,
    serverGateStatus: ServerGateStatus,
    authSession: AuthSessionState,
    installState: PubgCopyInstallState,
    isGateChecking: Boolean,
    operationHistory: List<OperationHistoryEntity>,
    lastDurationMs: Long?,
    onConnectShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onSelectEdition: (GameEditionInfo) -> Unit,
    onStartInstall: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onRefreshServerGate: () -> Unit,
    onLaunchPubg: () -> Unit
) {
    val s = LocalAppStrings.current
    // The edition must be chosen by the user in this session before copying is possible.
    var chosenKey by rememberSaveable { mutableStateOf<String?>(null) }

    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(installState.isRunning) {
        if (installState.isRunning) {
            val start = System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - start
                kotlinx.coroutines.delay(500L)
            }
        }
    }
    val shownDuration: Long? = when {
        installState.isRunning -> elapsedMs
        else -> lastDurationMs
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = s.appName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )

        // 1. Shizuku status & permissions
        GamingCard(glowBorderColor = if (shizukuStatus.isReadyForElevatedOps) NeonGreen else CarbonBorder) {
            Text(s.shizukuStatusTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            StatusLine(
                ok = shizukuStatus.isBinderAlive,
                text = if (shizukuStatus.isBinderAlive) s.shizukuConnected else s.shizukuDisconnected
            )
            StatusLine(
                ok = shizukuStatus.isPermissionGranted,
                text = if (shizukuStatus.isPermissionGranted) s.permissionGranted else s.permissionNotGranted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnectShizuku,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
                ) { Text(s.btnConnectRequestPermission, fontSize = 12.sp) }
                OutlinedButton(onClick = onRefreshShizuku) { Text(s.btnRefreshShizuku, fontSize = 12.sp) }
            }
        }

        // Account / server gate
        val gate = serverGateStatus
        val blockedText: String? = when {
            gate.isConfigured && !gate.isReachable -> s.serverStatusOffline
            gate.maintenance.isActive -> s.maintenanceTitle
            !authSession.isLoggedIn || gate.user == null -> s.homeSignInRequired
            gate.user.isBanned -> s.accountBannedTitle
            gate.subscription.required && !gate.subscription.isActive -> s.subscriptionInactiveBadge
            else -> null
        }
        GamingCard(glowBorderColor = if (blockedText == null) NeonGreen else WarningOrange) {
            Text(s.ansServerControlTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            StatusLine(
                ok = blockedText == null,
                text = blockedText ?: s.serverStatusConnected
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccountSheet) { Text(s.btnAccountAndPlan, fontSize = 12.sp) }
                OutlinedButton(onClick = onRefreshServerGate, enabled = !isGateChecking) {
                    Text(s.btnSyncServer, fontSize = 12.sp)
                }
            }
        }

        // 2. Mandatory PUBG edition picker
        GamingCard {
            Text(s.homeSelectEditionTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(s.homeSelectEditionHint, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            val orderedEditions = PRESET_GAME_EDITIONS.sortedBy { if (it.key.name == "PUBG_FLASH") 0 else 1 }
            orderedEditions.forEach { edition ->
                val picked = chosenKey == edition.key.name
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(enabled = !installState.isRunning) {
                            chosenKey = edition.key.name
                            onSelectEdition(edition)
                        }
                        .testTag("edition_${edition.key.name}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (picked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (picked) NeonGreen else CarbonBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (picked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (picked) NeonGreen else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(edition.title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(edition.packageName, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
            val pickedEdition = PRESET_GAME_EDITIONS.find { it.key.name == chosenKey }
            if (pickedEdition != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pickedEdition.defaultPufferPath,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        // 3. Download & copy, status, duration
        GamingCard {
            Text(s.homeStatusTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            val canStart = chosenKey != null && chosenKey == selectedEditionKey &&
                !installState.isRunning && blockedText == null
            Button(
                onClick = onStartInstall,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_download_copy"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
            ) {
                Text(
                    text = if (chosenKey == null) s.homePickEditionFirst else s.btnDownloadAndCopy,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            val hasState = installState.isRunning || installState.hasError || installState.progress > 0f
            if (installState.isRunning) {
                LinearProgressIndicator(
                    progress = { installState.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonGreen
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = if (hasState) installState.stepTitle else s.homeStatusIdle,
                fontSize = 13.sp,
                color = if (installState.hasError) ErrorRed else TextPrimary
            )
            if (shownDuration != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${s.durationLabel} ${formatDurationMs(shownDuration)}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            if (installState.canLaunchPubg) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onLaunchPubg) { Text(s.btnLaunchPubg) }
            }
        }

        // 4. Operation log
        GamingCard {
            Text(s.homeLogTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            val serverOps = operationHistory.filter { it.sourcePath.startsWith("ANS Server") }.take(8)
            if (serverOps.isEmpty()) {
                Text(s.homeLogEmpty, fontSize = 12.sp, color = TextSecondary)
            } else {
                val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
                serverOps.forEach { op ->
                    val ok = op.status == "SUCCESS"
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (ok) NeonGreen else ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(op.fileName, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = fmt.format(Date(op.timestamp)) +
                                    if (op.sha256Verified) "  SHA-256 ✓" else "",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            if (!ok && !op.errorMessage.isNullOrBlank()) {
                                Text(op.errorMessage, fontSize = 11.sp, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (ok) NeonGreen else WarningOrange,
            modifier = Modifier.size(18.dp)
        )
        Text(text, fontSize = 13.sp, color = TextPrimary)
    }
}
