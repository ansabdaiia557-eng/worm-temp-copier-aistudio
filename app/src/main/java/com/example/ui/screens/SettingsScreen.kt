package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.AppSettings
import com.example.server.AuthSessionState
import com.example.server.ServerGateStatus
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: AppSettings,
    authSession: AuthSessionState,
    serverGateStatus: ServerGateStatus,
    isGateChecking: Boolean,
    onLanguageChange: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onSignOut: () -> Unit
) {
    val s = LocalAppStrings.current
    val uriHandler = LocalUriHandler.current
    var checkedOnce by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = s.navSettings,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )

        // Language
        GamingCard {
            Text(s.settingLanguageTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LangButton("العربية", settings.languageCode == "ar") { onLanguageChange("ar") }
                LangButton("English", settings.languageCode != "ar") { onLanguageChange("en") }
            }
        }

        // Account (server-controlled)
        GamingCard {
            Text(s.settingsAccountSection, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            val email = serverGateStatus.user?.email ?: authSession.email
            if (authSession.isLoggedIn && email.isNotBlank()) {
                Text(email, fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccountSheet) { Text(s.btnAccountAndPlan, fontSize = 12.sp) }
                if (authSession.isLoggedIn) {
                    OutlinedButton(onClick = onSignOut) { Text(s.btnSignOut, fontSize = 12.sp) }
                }
            }
        }

        // Developer & Telegram
        GamingCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.settingsDeveloperLabel, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(s.settingsDeveloperName, color = NeonGreen, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(s.settingsTelegramLabel, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(serverGateStatus.telegram.channelUsername, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { uriHandler.openUri(serverGateStatus.telegram.channelUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
            ) { Text(s.btnOpenTelegram) }
        }

        // Update check
        GamingCard {
            Button(
                onClick = {
                    checkedOnce = true
                    onCheckUpdates()
                },
                enabled = !isGateChecking,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
            ) { Text(s.btnCheckUpdates) }
            if (checkedOnce && !isGateChecking) {
                Spacer(Modifier.height(8.dp))
                val update = serverGateStatus.appUpdate
                val msg = when {
                    !serverGateStatus.isReachable -> s.updateCheckFailedMsg
                    update != null -> "${s.updateAvailableMsg} ${update.versionName}"
                    else -> s.updateUpToDateMsg
                }
                Text(msg, fontSize = 13.sp, color = TextPrimary)
            }
        }

        // App info
        GamingCard {
            Text(s.settingsAppInfoTitle, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(s.appName, fontSize = 13.sp, color = TextPrimary)
            Text(s.appTagline, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text("${s.settingsVersionLabel}: ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LangButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
