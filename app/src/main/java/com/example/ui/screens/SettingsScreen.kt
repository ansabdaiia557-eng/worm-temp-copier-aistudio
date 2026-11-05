package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.shizuku.ShizukuStatus
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TacticalCyan

@Composable
fun SettingsScreen(
    settings: AppSettings,
    shizukuStatus: ShizukuStatus,
    onAutoShizukuChange: (Boolean) -> Unit,
    onConnectShizuku: () -> Unit,
    onConfirmOverwriteChange: (Boolean) -> Unit,
    onVerifySha256Change: (Boolean) -> Unit,
    onVibrateFailureChange: (Boolean) -> Unit,
    onKeepHistoryChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.settingsScreenTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Shizuku Settings
        item {
            GamingCard {
                SectionHeader(
                    icon = Icons.Default.Security,
                    title = strings.settingsShizukuSection
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = strings.settingAutoShizukuTitle,
                    description = strings.settingAutoShizukuDesc,
                    checked = settings.autoUseShizuku,
                    onCheckedChange = onAutoShizukuChange,
                    testTag = "switch_auto_shizuku"
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onConnectShizuku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shizuku_btn"),
                    border = BorderStroke(1.dp, NeonGreen)
                ) {
                    Text(
                        text = "${strings.btnConnectRequestPermission} (${if (shizukuStatus.isReadyForElevatedOps) "OK" else "Check"})",
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. File Protection & SHA-256 Settings
        item {
            GamingCard {
                SectionHeader(
                    icon = Icons.Default.VerifiedUser,
                    title = strings.settingsSafetySection
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = strings.settingConfirmOverwriteTitle,
                    description = strings.settingConfirmOverwriteDesc,
                    checked = settings.confirmOverwrite,
                    onCheckedChange = onConfirmOverwriteChange,
                    testTag = "switch_confirm_overwrite"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = CarbonBorder
                )
                SettingToggleRow(
                    title = strings.settingVerifySha256Title,
                    description = strings.settingVerifySha256Desc,
                    checked = settings.verifySha256,
                    onCheckedChange = onVerifySha256Change,
                    testTag = "switch_verify_sha256"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = CarbonBorder
                )
                SettingToggleRow(
                    title = strings.settingVibrateFailureTitle,
                    description = strings.settingVibrateFailureDesc,
                    checked = settings.vibrateOnFailure,
                    onCheckedChange = onVibrateFailureChange,
                    testTag = "switch_vibrate_failure"
                )
            }
        }

        // 3. History Retention Settings
        item {
            GamingCard {
                SectionHeader(
                    icon = Icons.Default.History,
                    title = strings.settingsHistorySection
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = strings.settingKeepHistoryTitle,
                    description = strings.settingKeepHistoryDesc,
                    checked = settings.keepHistory,
                    onCheckedChange = onKeepHistoryChange,
                    testTag = "switch_keep_history"
                )
            }
        }

        // 4. Language & Theme Appearance
        item {
            GamingCard {
                SectionHeader(
                    icon = Icons.Default.Palette,
                    title = strings.settingsAppearanceSection
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.settingLanguageTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.languageCode == "ar",
                            onClick = { onLanguageChange("ar") },
                            label = { Text("العربية (RTL)") },
                            modifier = Modifier.testTag("lang_ar_chip"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreenSurface,
                                selectedLabelColor = NeonGreen
                            )
                        )
                        FilterChip(
                            selected = settings.languageCode == "en",
                            onClick = { onLanguageChange("en") },
                            label = { Text("English (LTR)") },
                            modifier = Modifier.testTag("lang_en_chip"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreenSurface,
                                selectedLabelColor = NeonGreen
                            )
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = CarbonBorder
                )

                SettingToggleRow(
                    title = strings.settingDarkModeTitle,
                    description = strings.settingDarkModeDesc,
                    checked = settings.darkMode,
                    onCheckedChange = onDarkModeChange,
                    testTag = "switch_dark_mode"
                )
            }
        }

        // 5. About & Reset Settings
        item {
            GamingCard {
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = strings.settingsAboutSection
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Worm Temp Copier • Build v1.2.0 (Android 12+ / Shizuku 13.1.5)",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Default Target: $DEFAULT_PUFFER_TEMP_PATH",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onResetSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_settings_btn"),
                    border = BorderStroke(1.dp, ErrorRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.btnResetSettings,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianDark,
                checkedTrackColor = NeonGreen
            )
        )
    }
}
