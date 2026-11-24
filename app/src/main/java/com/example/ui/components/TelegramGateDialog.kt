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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianStroke
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val DEFAULT_TELEGRAM_CHANNEL_URL = "https://t.me/Kharbash_anas"

@Composable
fun TelegramGateDialog(
    channelUrl: String = DEFAULT_TELEGRAM_CHANNEL_URL,
    showServerVerification: Boolean = false,
    isVerifiedByServer: Boolean = false,
    isBusy: Boolean = false,
    onVerifyMembership: (String) -> Unit = {},
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var telegramUserIdInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { /* Require explicit user action */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("telegram_gate_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, ObsidianStroke),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NeonGreenSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVerifiedByServer) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram Channel",
                        tint = NeonGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.telegramGateTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.telegramGateSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.telegramGateWelcomeMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Join Telegram Channel Button
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl.ifBlank { DEFAULT_TELEGRAM_CHANNEL_URL })).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_join_telegram"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.btnJoinTelegramChannel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (showServerVerification && !isVerifiedByServer) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = telegramUserIdInput,
                        onValueChange = { input ->
                            telegramUserIdInput = input.filter { it.isDigit() }
                        },
                        label = { Text(strings.telegramUserIdLabel) },
                        supportingText = { Text(strings.telegramUserIdHint, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_user_id_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onVerifyMembership(telegramUserIdInput) },
                        enabled = !isBusy && telegramUserIdInput.length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_verify_telegram_membership"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TacticalCyan,
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
                        } else {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = strings.btnVerifyTelegramMembership,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isVerifiedByServer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.telegramVerifiedBadge,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Continue to App Button
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_continue_to_app"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianStroke),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = strings.btnContinueToApp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
