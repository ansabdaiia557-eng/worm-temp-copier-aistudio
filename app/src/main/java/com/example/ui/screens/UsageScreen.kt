package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UsageSessionEntity
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(
    sessions: List<UsageSessionEntity>,
    onClear: () -> Unit
) {
    val s = LocalAppStrings.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val ok = sessions.count { it.success }
    val failed = sessions.size - ok

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("usage_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = s.usageTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeonGreen
            )
        }
        item {
            GamingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Counter(s.usageTotalLabel, sessions.size.toString(), TextPrimary)
                    Counter(s.usageSuccessLabel, ok.toString(), NeonGreen)
                    Counter(s.usageFailedLabel, failed.toString(), ErrorRed)
                }
            }
        }
        if (sessions.isEmpty()) {
            item { Text(s.usageEmpty, fontSize = 13.sp, color = TextSecondary) }
        } else {
            items(sessions, key = { it.id }) { row ->
                GamingCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (row.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (row.success) NeonGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "${s.usageEditionLabel} ${row.editionTitle}",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = fmt.format(Date(row.timestamp)) +
                                    "  •  ${s.durationLabel} ${formatDurationMs(row.durationMs)}" +
                                    if (row.success) "  •  ${s.usageFilesLabel} ${row.filesCount}" else "",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            if (!row.success && !row.message.isNullOrBlank()) {
                                Text(row.message, fontSize = 11.sp, color = ErrorRed)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onClear) { Text(s.btnClearUsage) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}
