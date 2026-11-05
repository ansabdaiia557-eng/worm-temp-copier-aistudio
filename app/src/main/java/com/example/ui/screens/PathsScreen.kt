package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.data.GameEditionInfo
import com.example.data.GamePathAccessState
import com.example.data.PRESET_GAME_EDITIONS
import com.example.data.PathAccessStatus
import com.example.data.SavedPathEntity
import com.example.ui.components.GamingCard
import com.example.ui.components.PathAccessStatusBadge
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PathsScreen(
    savedPaths: List<SavedPathEntity>,
    activeDestinationPath: String,
    gameEditionStatuses: Map<String, GamePathAccessState>,
    shizukuReady: Boolean,
    checkPathAccess: (String, String?) -> PathAccessStatus,
    onSelectGameEdition: (GameEditionInfo) -> Unit,
    onChangeGameEditionPath: (GameEditionInfo, String) -> Unit,
    onSelectActivePath: (SavedPathEntity) -> Unit,
    onAddCustomPath: (name: String, path: String, isFav: Boolean, setActive: Boolean) -> Unit,
    onEditSavedPath: (SavedPathEntity, String, String) -> Unit,
    onToggleFavorite: (SavedPathEntity) -> Unit,
    onDeletePath: (SavedPathEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPath by remember { mutableStateOf<SavedPathEntity?>(null) }
    var editingGameEdition by remember { mutableStateOf<Pair<GameEditionInfo, String>?>(null) }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("paths_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.pathsScreenTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.pathsScreenSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_custom_path_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = strings.btnAddCustomPath, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 1. GAME PATHS (PUBG MOBILE 5 EDITIONS) SECTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.gamePathsSectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = strings.gamePathsSectionSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(PRESET_GAME_EDITIONS, key = { it.key.name }) { edition ->
            val isCurrentActive = activeDestinationPath == edition.defaultPufferPath
            val accessState = gameEditionStatuses[edition.packageName] ?: GamePathAccessState.NOT_FOUND

            GamingCard(
                glowBorderColor = if (isCurrentActive) NeonGreen else CarbonBorder,
                modifier = Modifier.testTag("game_edition_card_${edition.key.name}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = edition.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentActive) NeonGreen else MaterialTheme.colorScheme.onSurface
                            )
                            if (isCurrentActive) {
                                Surface(
                                    color = NeonGreenSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = strings.activePathBadge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Package: ${edition.packageName}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = TacticalCyan
                        )
                    }

                    // Status Badge (Available, Not Found, Permission Required)
                    GameEditionStatusBadge(accessState)
                }

                if (edition.isProposedOnly) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• ${strings.proposedPathNotice}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = WarningOrange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Full Path Display Box
                Surface(
                    color = Color(0xFF0E1215),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = edition.defaultPufferPath,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons: Copy Path / Change Path / Set as Destination
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { copyToClipboard(edition.defaultPufferPath, edition.title) },
                        modifier = Modifier.testTag("copy_path_${edition.key.name}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TacticalCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = strings.btnCopyPath, color = TacticalCyan, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { editingGameEdition = edition to edition.defaultPufferPath },
                        modifier = Modifier.testTag("change_path_${edition.key.name}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CarbonBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = strings.btnChangePath, color = TextPrimary, fontSize = 12.sp)
                    }

                    if (!isCurrentActive) {
                        Button(
                            onClick = { onSelectGameEdition(edition) },
                            modifier = Modifier.testTag("select_edition_${edition.key.name}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = ObsidianDark
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = strings.btnSelectAsDestination, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. SAVED & CUSTOM PATHS SECTION
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = TacticalCyan,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.allSavedPathsSection,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(savedPaths, key = { it.id }) { entity ->
            val isActive = entity.path == activeDestinationPath
            val accessStatus = checkPathAccess(entity.path, entity.treeUriString)

            GamingCard(
                glowBorderColor = if (isActive) NeonGreen else CarbonBorder,
                modifier = Modifier.testTag("saved_path_card_${entity.id}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = if (isActive) NeonGreen else TacticalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = entity.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleFavorite(entity) },
                            modifier = Modifier.testTag("fav_btn_${entity.id}")
                        ) {
                            Icon(
                                imageVector = if (entity.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (entity.isFavorite) WarningOrange else TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { editingPath = entity },
                            modifier = Modifier.testTag("edit_path_btn_${entity.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit path",
                                tint = TacticalCyan
                            )
                        }
                        if (!entity.isDefault) {
                            IconButton(
                                onClick = { onDeletePath(entity) },
                                modifier = Modifier.testTag("delete_path_btn_${entity.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete path",
                                    tint = ErrorRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFF0E1215),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entity.path,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PathAccessStatusBadge(
                        status = accessStatus,
                        shizukuReady = shizukuReady,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!isActive) {
                        OutlinedButton(
                            onClick = { onSelectActivePath(entity) },
                            modifier = Modifier.testTag("use_path_btn_${entity.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NeonGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = strings.btnUsePath, color = NeonGreen, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog: Edit Game Edition Path
    editingGameEdition?.let { (edition, currentVal) ->
        var editedVal by remember { mutableStateOf(currentVal) }
        AlertDialog(
            onDismissRequest = { editingGameEdition = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "${strings.btnChangePath} - ${edition.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize destination folder for ${edition.packageName}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedVal,
                        onValueChange = { editedVal = it },
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onChangeGameEditionPath(edition, editedVal)
                        editingGameEdition = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = ObsidianDark)
                ) {
                    Text(text = strings.btnSavePath, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingGameEdition = null }) {
                    Text(text = strings.btnDismiss, color = TextSecondary)
                }
            }
        )
    }

    if (showAddDialog) {
        var nameInput by remember { mutableStateOf("") }
        var pathInput by remember { mutableStateOf("/storage/emulated/0/") }
        var isFav by remember { mutableStateOf(true) }
        var setActive by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = strings.dialogAddPathTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(strings.pathNameLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_path_name_input")
                    )
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text(strings.pathValueLabel) },
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_path_value_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFav, onCheckedChange = { isFav = it })
                        Text(text = strings.favoritePathsSection, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setActive, onCheckedChange = { setActive = it })
                        Text(text = strings.btnUsePath, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomPath(nameInput, pathInput, isFav, setActive)
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("save_new_path_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Text(text = strings.btnSavePath, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(text = strings.btnDismiss, color = TextSecondary)
                }
            }
        )
    }

    editingPath?.let { target ->
        var editName by remember(target) { mutableStateOf(target.name) }
        var editPathVal by remember(target) { mutableStateOf(target.path) }

        AlertDialog(
            onDismissRequest = { editingPath = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = strings.dialogEditPathTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(strings.pathNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPathVal,
                        onValueChange = { editPathVal = it },
                        label = { Text(strings.pathValueLabel) },
                        enabled = !target.isDefault,
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditSavedPath(target, editName, editPathVal)
                        editingPath = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = ObsidianDark
                    )
                ) {
                    Text(text = strings.btnSavePath, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPath = null }) {
                    Text(text = strings.btnDismiss, color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun GameEditionStatusBadge(state: GamePathAccessState) {
    val strings = LocalAppStrings.current
    val (color, bg, icon, text) = when (state) {
        GamePathAccessState.AVAILABLE -> Tuple4(
            NeonGreen,
            NeonGreenSurface,
            Icons.Default.VerifiedUser,
            strings.statusAvailable
        )
        GamePathAccessState.PERMISSION_REQUIRED -> Tuple4(
            WarningOrange,
            WarningOrangeSurface,
            Icons.Default.Security,
            strings.statusPermissionRequired
        )
        GamePathAccessState.NOT_FOUND -> Tuple4(
            ErrorRed,
            ErrorRedSurface,
            Icons.Default.ErrorOutline,
            strings.statusNotFound
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
