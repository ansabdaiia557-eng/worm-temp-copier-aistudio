package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.FileOperationEngine
import com.example.server.AppUpdateInfo
import com.example.server.AuthSessionState
import com.example.server.ServerGateStatus
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianStroke
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun MaintenanceBlockingOverlay(
    gateStatus: ServerGateStatus,
    isChecking: Boolean,
    onRefreshNow: () -> Unit
) {
    val strings = LocalAppStrings.current
    val reasonText = if (strings.isArabic) {
        gateStatus.maintenance.reasonAr ?: gateStatus.blockReasonAr ?: strings.maintenanceTitle
    } else {
        gateStatus.maintenance.reasonEn ?: gateStatus.blockReasonEn ?: strings.maintenanceTitle
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark.copy(alpha = 0.96f))
            .padding(24.dp)
            .testTag("maintenance_blocking_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianCard,
            border = BorderStroke(1.5.dp, WarningOrange.copy(alpha = 0.7f)),
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(WarningOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Engineering,
                        contentDescription = strings.maintenanceTitle,
                        tint = WarningOrange,
                        modifier = Modifier.size(38.dp)
                    )
                }

                if (gateStatus.maintenance.isScheduled) {
                    Surface(
                        color = WarningOrange.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = strings.maintenanceScheduledBadge,
                            color = WarningOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = strings.maintenanceTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                gateStatus.maintenance.endsAt?.let { ends ->
                    Text(
                        text = strings.maintenanceEndsAtLabel(ends.take(19).replace("T", " ")),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = WarningOrange
                    )
                }

                Text(
                    text = strings.maintenanceAutoResumeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TacticalCyan,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onRefreshNow,
                    enabled = !isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_check_maintenance_status"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianDark,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.btnCheckServerNow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AccountAuthAndSubscriptionDialog(
    authSession: AuthSessionState,
    gateStatus: ServerGateStatus,
    isBusy: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onSignOut: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember(authSession.email) { mutableStateOf(authSession.email) }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var activationCode by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("account_auth_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, ObsidianStroke),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (!authSession.isLoggedIn) {
                                if (isSignUpMode) strings.authTitleSignUp else strings.authTitleSignIn
                            } else {
                                strings.btnAccountAndPlan
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_account_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.btnDismiss,
                            tint = TextSecondary
                        )
                    }
                }

                // Banned banner if applicable
                if (gateStatus.user?.isBanned == true) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ErrorRedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = strings.accountBannedTitle,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                gateStatus.user.banReason?.let { reason ->
                                    Text(
                                        text = reason,
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (!authSession.isLoggedIn) {
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(strings.authDisplayNameLabel) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_display_name_input")
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.authEmailLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.authPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                onSignUp(email, password, displayName)
                            } else {
                                onSignIn(email, password)
                            }
                        },
                        enabled = !isBusy && email.isNotBlank() && password.length >= 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_auth_submit"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = ObsidianDark
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ObsidianDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isSignUpMode) strings.btnCreateAccount else strings.btnSignIn,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = { isSignUpMode = !isSignUpMode },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_toggle_auth_mode")
                    ) {
                        Text(
                            text = if (isSignUpMode) strings.btnSwitchToSignIn else strings.btnSwitchToSignUp,
                            color = TacticalCyan
                        )
                    }
                } else {
                    // Signed-in Profile Summary
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ObsidianDark,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CarbonBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = gateStatus.user?.displayName?.ifBlank { authSession.email }
                                        ?: authSession.email,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Surface(
                                    color = NeonGreenSurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = (gateStatus.user?.role ?: "user").uppercase(),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = authSession.email,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = CarbonBorder)

                    // Subscription & Activation Code Section
                    Text(
                        text = strings.subscriptionSectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )

                    val subActive = gateStatus.subscription.isActive
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (subActive) NeonGreenSurface else Color(0xFF231B12),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (subActive) NeonGreen.copy(alpha = 0.6f) else WarningOrange.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (subActive) Icons.Filled.CheckCircle else Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = if (subActive) NeonGreen else WarningOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (subActive) strings.subscriptionActiveBadge else strings.subscriptionInactiveBadge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (subActive) NeonGreen else WarningOrange
                                )
                            }
                            gateStatus.subscription.planName?.let { plan ->
                                Text(
                                    text = plan,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                            gateStatus.subscription.endsAt?.let { ends ->
                                Text(
                                    text = strings.subscriptionExpiresLabel(ends.take(16).replace("T", " ")),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = activationCode,
                        onValueChange = { activationCode = it.uppercase() },
                        label = { Text(strings.activationCodeLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_code_input")
                    )

                    Button(
                        onClick = { onRedeemCode(activationCode) },
                        enabled = !isBusy && activationCode.trim().length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_redeem_activation_code"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TacticalCyan,
                            contentColor = ObsidianDark
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.btnRedeemCode,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_sign_out"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.btnSignOut,
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismissOptional: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val changelog = if (strings.isArabic) {
        updateInfo.changelogAr.ifBlank { updateInfo.changelogEn }
    } else {
        updateInfo.changelogEn.ifBlank { updateInfo.changelogAr }
    }

    Dialog(
        onDismissRequest = {
            if (!updateInfo.isMandatory) onDismissOptional()
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isMandatory,
            dismissOnClickOutside = !updateInfo.isMandatory
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = ObsidianCard,
            border = BorderStroke(1.5.dp, if (updateInfo.isMandatory) ErrorRed else NeonGreen),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = if (updateInfo.isMandatory) ErrorRed else NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (updateInfo.isMandatory) {
                                strings.appUpdateMandatoryTitle
                            } else {
                                strings.appUpdateOptionalTitle
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = strings.appUpdateVersionLabel(updateInfo.versionName, updateInfo.versionCode),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = NeonGreen
                        )
                    }
                }

                if (changelog.isNotBlank()) {
                    Text(
                        text = changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (updateInfo.sizeBytes > 0L || updateInfo.sha256.isNotBlank()) {
                    Surface(
                        color = ObsidianDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CarbonBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (updateInfo.sizeBytes > 0L) {
                                Text(
                                    text = "Size: ${FileOperationEngine.formatFileSize(updateInfo.sizeBytes)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                            if (updateInfo.sha256.isNotBlank()) {
                                Text(
                                    text = "SHA-256: ${updateInfo.sha256.take(24)}...",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TacticalCyan
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (updateInfo.downloadUrl.isNotBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_download_app_update"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Text(
                        text = strings.btnDownloadAppUpdate,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!updateInfo.isMandatory) {
                    TextButton(
                        onClick = onDismissOptional,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_dismiss_optional_update")
                    ) {
                        Text(text = strings.btnLater, color = TextSecondary)
                    }
                }
            }
        }
    }
}
