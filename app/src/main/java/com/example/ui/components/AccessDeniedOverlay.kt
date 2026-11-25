package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.server.ServerGateStatus
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

val ACCESS_DENIED_CODES = setOf(
    "DEVICE_MISMATCH", "TAMPERED_APP", "DEVICE_REQUIRED", "ACCOUNT_BANNED", "SUBSCRIPTION_REQUIRED"
)

@Composable
fun AccessDeniedOverlay(
    gateStatus: ServerGateStatus,
    onOpenAccount: () -> Unit
) {
    val strings = LocalAppStrings.current
    val ar = strings.isArabic
    val ctx = LocalContext.current
    val code = gateStatus.blockReasonCode ?: ""
    val title = when (code) {
        "DEVICE_MISMATCH", "DEVICE_REQUIRED" -> if (ar) "الحساب ده مربوط بجهاز تاني" else "Account linked to another device"
        "TAMPERED_APP" -> if (ar) "نسخة التطبيق غير رسمية" else "Unofficial app version"
        "ACCOUNT_BANNED" -> if (ar) "الحساب ده محظور" else "Account banned"
        else -> if (ar) "محتاج اشتراك عشان تكمل" else "Subscription required"
    }
    val body = if (ar) {
        gateStatus.blockReasonAr ?: title
    } else {
        gateStatus.blockReasonEn ?: title
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .padding(24.dp)
            .testTag("access_denied_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔒", style = MaterialTheme.typography.displayMedium)
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            Button(
                onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Kharbash_anas")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (ar) "اشترك دلوقتي" else "Subscribe now") }
            if (code == "SUBSCRIPTION_REQUIRED") {
                OutlinedButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ar) "عندي كود تفعيل" else "I have an activation code")
                }
            }
        }
    }
}
