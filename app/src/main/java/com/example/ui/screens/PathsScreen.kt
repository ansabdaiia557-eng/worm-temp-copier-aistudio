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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DEFAULT_PUFFER_TEMP_PATH
import com.example.data.PathAccessStatus
import com.example.data.SavedPathEntity
import com.example.ui.components.GamingCard
import com.example.ui.components.PathAccessStatusBadge
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun PathsScreen(
    savedPaths: List<SavedPathEntity>,
    activeDestinationPath: String,
    shizukuReady: Boolean,
    checkPathAccess: (String, String?) -> PathAccessStatus,
    onSelectActivePath: (SavedPathEntity) -> Unit,
    onAddCustomPath: (name: String, path: String, isFav: Boolean, setActive: Boolean) -> Unit,
    onEditSavedPath: (SavedPathEntity, String, String) -> Unit,
    onToggleFavorite: (SavedPathEntity) -> Unit,
    onDeletePath: (SavedPathEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPath by remember { mutableStateOf<SavedPathEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("paths_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
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
                                modifier = Modifier.size(22.dp)
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

                    // Badges row (Default / Active)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (entity.isDefault || entity.path == DEFAULT_PUFFER_TEMP_PATH) {
                            Surface(
                                color = TacticalCyanSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = strings.defaultPathBadge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TacticalCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (isActive) {
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
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
