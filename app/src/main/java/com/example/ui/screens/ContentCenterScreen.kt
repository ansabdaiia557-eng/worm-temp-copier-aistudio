package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContentCategory
import com.example.data.ContentItem
import com.example.data.GameEditionKey
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianStroke
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningYellow

@Composable
fun ContentCenterScreen(
    contentItems: List<ContentItem>,
    currentEditionKey: GameEditionKey,
    activeEditionTitle: String,
    downloadingItemId: String?,
    downloadProgress: Float,
    onUseContentItem: (ContentItem) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        ContentCategory.ALL_FEATURES to strings.tabAllFeatures,
        ContentCategory.SKINS to strings.tabSkins,
        ContentCategory.AIM_SETTINGS to strings.tabAimSettings
    )

    val currentCategory = tabs[selectedTabIndex].first

    val filteredItems = remember(contentItems, currentCategory) {
        if (currentCategory == ContentCategory.ALL_FEATURES) {
            contentItems
        } else {
            contentItems.filter { it.category == currentCategory }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("content_center_screen")
    ) {
        // Screen Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonGreenSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = strings.contentCenterTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = strings.contentCenterSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 3 Tabs (All Features, Skins, Aim Settings)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = ObsidianCard,
            contentColor = NeonGreen,
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ObsidianStroke)
                )
            },
            modifier = Modifier.testTag("content_center_tabs")
        ) {
            tabs.forEachIndexed { index, (_, title) ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.testTag("tab_content_$index"),
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonGreen else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        // Disclaimer Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = ObsidianCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ObsidianStroke)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = strings.contentDisclaimerNotice,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // List of Content Cards
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                ContentItemCard(
                    item = item,
                    currentEditionKey = currentEditionKey,
                    activeEditionTitle = activeEditionTitle,
                    isDownloading = downloadingItemId == item.id,
                    downloadProgress = if (downloadingItemId == item.id) downloadProgress else 0f,
                    onUseClick = { onUseContentItem(item) }
                )
            }
        }
    }
}

@Composable
private fun ContentItemCard(
    item: ContentItem,
    currentEditionKey: GameEditionKey,
    activeEditionTitle: String,
    isDownloading: Boolean,
    downloadProgress: Float,
    onUseClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isCompatible = item.compatibleEditions.contains(currentEditionKey)

    val categoryIcon: ImageVector = when (item.category) {
        ContentCategory.SKINS -> Icons.Filled.Palette
        ContentCategory.AIM_SETTINGS -> Icons.Filled.SportsEsports
        ContentCategory.ALL_FEATURES -> Icons.Filled.Security
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("content_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, if (isCompatible) ObsidianStroke else WarningYellow.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Category Badge + Version + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (item.category) {
                            ContentCategory.SKINS -> strings.tabSkins
                            ContentCategory.AIM_SETTINGS -> strings.tabAimSettings
                            ContentCategory.ALL_FEATURES -> strings.tabAllFeatures
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Version tag
                    Surface(
                        color = ObsidianDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.version,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Date tag
                    Text(
                        text = item.updatedAt,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Meta Info Row (Size, Status, Integrity Hash Badge - no internal storage path!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // File size
                    val sizeFormatted = formatFileSize(item.sizeBytes)
                    Text(
                        text = sizeFormatted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )

                    // Status Badge
                    val statusText = if (item.isDownloaded) strings.statusContentDownloaded else strings.statusContentAvailable
                    val statusColor = if (item.isDownloaded) NeonGreen else TextSecondary
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isDownloaded) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Verified SHA-256 Badge
                    Surface(
                        color = NeonGreenSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "SHA-256 Verified",
                                fontSize = 10.sp,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Compatibility Warning if active PUBG edition is not in supported list
            if (!isCompatible) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = strings.incompatibleEditionNotice(item.title, activeEditionTitle),
                        fontSize = 11.sp,
                        color = WarningYellow
                    )
                }
            }

            // Downloading Progress Indicator
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonGreen,
                    trackColor = ObsidianDark
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button: "Use File"
            Button(
                onClick = onUseClick,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_use_content_${item.id}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompatible) NeonGreen else WarningYellow,
                    contentColor = ObsidianDark
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ObsidianDark,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.btnDownloading,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (item.isDownloaded) Icons.Filled.PlayArrow else Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.btnUseFile,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}
