package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FileSortOption
import com.example.data.SelectedFileItem
import com.example.domain.FileOperationEngine
import com.example.ui.components.FileExtensionBadgeIcon
import com.example.ui.components.GamingCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenSurface
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileManagerScreen(
    files: List<SelectedFileItem>,
    searchQuery: String,
    sortOption: FileSortOption,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (FileSortOption) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onRemoveFile: (String) -> Unit,
    onComputeFileHash: (String) -> Unit,
    onPickMoreFiles: () -> Unit,
    onGenerateTestFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val allSelected = remember(files) { files.isNotEmpty() && files.all { it.isSelected } }

    val filteredAndSorted = remember(files, searchQuery, sortOption) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) {
            files
        } else {
            files.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.extension.lowercase(Locale.ROOT).contains(q)
            }
        }
        when (sortOption) {
            FileSortOption.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            FileSortOption.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            FileSortOption.DATE -> filtered.sortedByDescending { it.lastModified }
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("file_manager_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.fileManagerTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.fileManagerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onPickMoreFiles,
                modifier = Modifier.testTag("files_add_more_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = ObsidianDark
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnAddMoreFiles, fontWeight = FontWeight.Bold)
            }
        }

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_search_input"),
            placeholder = { Text(text = strings.searchFilesPlaceholder) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonGreen)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        // Sort Chips & Select All Bar
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                SortChipItem(
                    label = strings.sortByName,
                    selected = sortOption == FileSortOption.NAME,
                    onClick = { onSortOptionChange(FileSortOption.NAME) },
                    testTag = "sort_name_chip"
                )
                SortChipItem(
                    label = strings.sortBySize,
                    selected = sortOption == FileSortOption.SIZE,
                    onClick = { onSortOptionChange(FileSortOption.SIZE) },
                    testTag = "sort_size_chip"
                )
                SortChipItem(
                    label = strings.sortByDate,
                    selected = sortOption == FileSortOption.DATE,
                    onClick = { onSortOptionChange(FileSortOption.DATE) },
                    testTag = "sort_date_chip"
                )
            }

            if (files.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onSelectAll(!allSelected) },
                    modifier = Modifier.testTag("select_all_files_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allSelected) strings.btnDeselectAll else strings.btnSelectAll,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = strings.fileSha256TapHint,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = TacticalCyan
        )

        if (filteredAndSorted.isEmpty()) {
            GamingCard(modifier = Modifier.padding(top = 20.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = strings.emptyFilesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.emptyFilesDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onGenerateTestFiles,
                        border = BorderStroke(1.dp, TacticalCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = strings.btnCreateTestFiles, color = TacticalCyan)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredAndSorted, key = { it.id }) { item ->
                    GamingCard(
                        glowBorderColor = if (item.isSelected) NeonGreen.copy(alpha = 0.5f) else CarbonBorder,
                        modifier = Modifier
                            .clickable { onToggleFileSelection(item.id) }
                            .testTag("file_item_card_${item.name}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { onToggleFileSelection(item.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonGreen,
                                        checkmarkColor = ObsidianDark
                                    )
                                )
                                FileExtensionBadgeIcon(extension = item.extension)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ".${item.extension.uppercase()} • ${FileOperationEngine.formatFileSize(item.sizeBytes)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = NeonGreen
                                    )
                                    Text(
                                        text = dateFormatter.format(Date(item.lastModified)),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onComputeFileHash(item.id) },
                                    modifier = Modifier.testTag("hash_btn_${item.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Compute SHA-256",
                                        tint = if (item.computedSha256 != null) NeonGreen else TacticalCyan
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveFile(item.id) },
                                    modifier = Modifier.testTag("remove_btn_${item.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove file",
                                        tint = ErrorRed
                                    )
                                }
                            }
                        }

                        if (item.computedSha256 != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = NeonGreenSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SHA-256: ${item.computedSha256}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = NeonGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, fontSize = 12.sp) },
        modifier = Modifier.testTag(testTag),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonGreenSurface,
            selectedLabelColor = NeonGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = NeonGreen
        )
    )
}
