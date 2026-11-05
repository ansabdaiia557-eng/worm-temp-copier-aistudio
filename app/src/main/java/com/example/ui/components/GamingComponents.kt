package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.OperationType
import com.example.data.PathAccessStatus
import com.example.ui.PendingTransferConfirmation
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
import com.example.ui.theme.WarningOrangeSurface

@Composable
fun GamingCard(
    modifier: Modifier = Modifier,
    glowBorderColor: Color = CarbonBorder,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, glowBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun PulsingStatusDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "status_pulse")
    val scaleAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = scaleAlpha * 0.35f))
        )
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PathAccessStatusBadge(
    status: PathAccessStatus,
    shizukuReady: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (bgColor, textColor, icon, text) = when (status) {
        PathAccessStatus.DIRECT_WRITABLE -> Quad(
            NeonGreenSurface,
            NeonGreen,
            Icons.Default.VerifiedUser,
            strings.pathStatusDirectWritable
        )
        PathAccessStatus.WILL_CREATE_DIR -> Quad(
            TacticalCyanSurface,
            TacticalCyan,
            Icons.Default.FolderSpecial,
            strings.pathStatusWillCreateDir
        )
        PathAccessStatus.NEEDS_SHIZUKU -> if (shizukuReady) {
            Quad(
                NeonGreenSurface,
                NeonGreen,
                Icons.Default.Security,
                "Shizuku Ready • ${strings.pathStatusNeedsShizuku}"
            )
        } else {
            Quad(
                WarningOrangeSurface,
                WarningOrange,
                Icons.Default.WarningAmber,
                strings.pathStatusNeedsShizuku
            )
        }
        PathAccessStatus.INVALID -> Quad(
            ErrorRedSurface,
            ErrorRed,
            Icons.Default.ErrorOutline,
            strings.pathStatusInvalid
        )
    }

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun FileExtensionBadgeIcon(
    extension: String,
    modifier: Modifier = Modifier
) {
    val ext = extension.lowercase()
    val (tint, bg, icon) = when (ext) {
        "pak", "obb", "dat", "bin" -> Triple(NeonGreen, NeonGreenSurface, Icons.Default.Memory)
        "cfg", "ini", "conf", "prop" -> Triple(TacticalCyan, TacticalCyanSurface, Icons.Default.SettingsSuggest)
        "json", "xml", "txt", "log" -> Triple(WarningOrange, WarningOrangeSurface, Icons.Default.Code)
        "zip", "rar", "7z", "tar" -> Triple(Color(0xFFE040FB), Color(0xFF2A1230), Icons.Default.Archive)
        "png", "jpg", "jpeg", "webp" -> Triple(Color(0xFF00E5FF), Color(0xFF0B262B), Icons.Default.Image)
        else -> Triple(TextSecondary, Color(0xFF1F262E), Icons.Default.Description)
    }

    Surface(
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = extension,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = ext.take(4).uppercase(),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = tint
            )
        }
    }
}

@Composable
fun TransferConfirmationDialog(
    pending: PendingTransferConfirmation,
    destinationPath: String,
    selectedCount: Int,
    onConfirmOverwriteAll: () -> Unit,
    onConfirmSkipExisting: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val hasConflicts = pending.conflictingFileNames.isNotEmpty()
    val isMove = pending.operationType == OperationType.MOVE

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (hasConflicts) WarningOrange else TacticalCyan,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (hasConflicts) strings.confirmOverwriteTitle else strings.confirmMoveTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMove) {
                    Text(
                        text = strings.confirmMoveMessage(selectedCount, destinationPath),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasConflicts) {
                    val sampleNames = pending.conflictingFileNames.take(4).joinToString("\n• ", prefix = "• ")
                    Text(
                        text = strings.confirmOverwriteMessage(sampleNames),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningOrange
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasConflicts) {
                    OutlinedButton(
                        onClick = onConfirmSkipExisting,
                        modifier = Modifier.testTag("dialog_skip_existing_btn"),
                        border = BorderStroke(1.dp, TacticalCyan)
                    ) {
                        Text(text = strings.btnSkipExisting, color = TacticalCyan)
                    }
                }
                Button(
                    onClick = onConfirmOverwriteAll,
                    modifier = Modifier.testTag("dialog_confirm_transfer_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMove) TacticalCyan else NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Text(
                        text = if (hasConflicts) strings.btnOverwriteAll else strings.btnConfirm,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_btn")
            ) {
                Text(text = strings.btnDismiss, color = TextSecondary)
            }
        }
    )
}

@Composable
fun SplashScreenOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(450)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "splash_scan")
        val scanProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scan_line"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianDark),
            contentAlignment = Alignment.Center
        ) {
            // Subtle cyber grid & animated neon green scan line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val scanY = h * (0.25f + 0.5f * scanProgress)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonGreen.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, scanY),
                    end = Offset(w, scanY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF15191D),
                    border = BorderStroke(2.dp, NeonGreen)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_launcher_icon_1793784567802),
                        contentDescription = "Worm Temp Copier Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "WORM TEMP COPIER",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "SHIZUKU FILE & PAK TRANSFER UTILITY",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = NeonGreen,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Animated neon bar
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1F272E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scanProgress.coerceIn(0.15f, 1f))
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonGreen.copy(alpha = 0.4f), NeonGreen)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = NeonGreenSurface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "BUILD v1.2.0 • SHA-256 VERIFIED",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = NeonGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
