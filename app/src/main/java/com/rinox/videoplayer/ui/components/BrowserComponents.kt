package com.rinox.videoplayer.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinox.videoplayer.data.BrowserCardFields
import com.rinox.videoplayer.data.BrowserLayoutMode
import com.rinox.videoplayer.data.BrowserViewMode
import com.rinox.videoplayer.data.SortDirection
import com.rinox.videoplayer.data.SortOption
import com.rinox.videoplayer.data.VideoFolder
import com.rinox.videoplayer.data.VideoItem
import com.rinox.videoplayer.ui.theme.DarkCard
import com.rinox.videoplayer.ui.theme.RinoxCyan
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
@Composable
fun VideoThumbnailBox(
    video: VideoItem,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(video.path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !video.isPrivate) {
                    context.contentResolver.loadThumbnail(video.uri, Size(320, 180), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(video.path)
                    val frame = retriever.getFrameAtTime(1_500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    frame
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1E26)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = video.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(30.dp)
            )
        }

        // Duration badge at bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 5.dp, bottom = if (video.watchProgressFraction > 0.01f) 7.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = FormatUtils.formatDuration(video.durationMs),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // MX Player Pro style red/accent Watch Progress Bar at bottom edge
        if (video.watchProgressFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(video.watchProgressFraction)
                        .fillMaxHeight()
                        .background(Color(0xFFE53935))
                )
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCardItem(
    folder: VideoFolder,
    isDark: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
    onRenameFolder: () -> Unit,
    onCopyFolder: () -> Unit,
    onMoveFolder: () -> Unit,
    onHideFolder: () -> Unit,
    onProperties: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cardBg = if (isDark) DarkCard else Color(0xFFF3F5F8)
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val subColor = if (isDark) Color(0xFF9AA3B2) else Color(0xFF5A6270)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (folder.hasUnwatched) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("NEW", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${folder.videoCount} videos • ${FormatUtils.formatSize(folder.totalSizeBytes)}",
                color = subColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Folder Options", tint = subColor)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = DarkCard
            ) {
                DropdownMenuItem(
                    text = { Text("Play All", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = RinoxCyan) },
                    onClick = { menuExpanded = false; onPlayAll() }
                )
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Color.White) },
                    onClick = { menuExpanded = false; onRenameFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Copy Folder to...", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = Color.White) },
                    onClick = { menuExpanded = false; onCopyFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Move Folder to...", color = Color.White) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = Color.White) },
                    onClick = { menuExpanded = false; onMoveFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Hide Folder", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White) },
                    onClick = { menuExpanded = false; onHideFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Properties", color = Color.White) },
                    leadingIcon = { Icon(Icons.Rounded.Info, null, tint = Color.White) },
                    onClick = { menuExpanded = false; onProperties() }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = Color(0xFFEF5350)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF5350)) },
                    onClick = { menuExpanded = false; onDeleteFolder() }
                )
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCardItem(
    video: VideoItem,
    isDark: Boolean,
    accentColor: Color,
    fields: BrowserCardFields = BrowserCardFields(),
    isGrid: Boolean = false,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cardBg = if (isDark) DarkCard else Color(0xFFF3F5F8)
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val subColor = if (isDark) Color(0xFF9AA3B2) else Color(0xFF5A6270)

    val metaParts = remember(video, fields) {
        buildList {
            if (fields.showResolution) add(video.resolutionLabel)
            if (fields.showSize) add(FormatUtils.formatSize(video.sizeBytes))
            if (fields.showFps) add("${video.fps} FPS")
            if (fields.showExtension) {
                val ext = video.displayName.substringAfterLast('.', "").uppercase()
                if (ext.isNotEmpty()) add(ext)
            }
        }.joinToString(" • ")
    }

    if (isGrid) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                .padding(8.dp)
        ) {
            VideoThumbnailBox(
                video = video,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.displayName,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = subColor, modifier = Modifier.size(18.dp))
                    }
                    VideoOptionsDropdown(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        isPrivate = video.isPrivate,
                        onPlay = onPlay,
                        onRename = onRename,
                        onCopyTo = onCopyTo,
                        onMoveTo = onMoveTo,
                        onTogglePrivacy = onTogglePrivacy,
                        onShare = onShare,
                        onProperties = onProperties,
                        onDelete = onDelete
                    )
                }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts,
                    color = subColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnailBox(
                video = video,
                accentColor = accentColor,
                modifier = Modifier.size(width = 116.dp, height = 68.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fields.showNewTag && !video.isWatched && video.watchProgressFraction <= 0.01f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("NEW", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = metaParts,
                        color = subColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (fields.showPath) {
                    Text(
                        text = video.folderPath,
                        color = subColor.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Video Options", tint = subColor)
                }
                VideoOptionsDropdown(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    isPrivate = video.isPrivate,
                    onPlay = onPlay,
                    onRename = onRename,
                    onCopyTo = onCopyTo,
                    onMoveTo = onMoveTo,
                    onTogglePrivacy = onTogglePrivacy,
                    onShare = onShare,
                    onProperties = onProperties,
                    onDelete = onDelete
                )
            }
        }
    }
}
@Composable
private fun VideoOptionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isPrivate: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = DarkCard
    ) {
        DropdownMenuItem(
            text = { Text("Play", color = Color.White) },
            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = RinoxCyan) },
            onClick = { onDismiss(); onPlay() }
        )
        DropdownMenuItem(
            text = { Text("Rename", color = Color.White) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Color.White) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Copy to...", color = Color.White) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = Color.White) },
            onClick = { onDismiss(); onCopyTo() }
        )
        DropdownMenuItem(
            text = { Text("Move to...", color = Color.White) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = Color.White) },
            onClick = { onDismiss(); onMoveTo() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (isPrivate) "Unhide from Privacy" else "Move to Privacy",
                    color = Color.White
                )
            },
            leadingIcon = {
                Icon(
                    if (isPrivate) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    null,
                    tint = RinoxCyan
                )
            },
            onClick = { onDismiss(); onTogglePrivacy() }
        )
        DropdownMenuItem(
            text = { Text("Share", color = Color.White) },
            leadingIcon = { Icon(Icons.Rounded.Share, null, tint = Color.White) },
            onClick = { onDismiss(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("Properties", color = Color.White) },
            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = Color.White) },
            onClick = { onDismiss(); onProperties() }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = Color(0xFFEF5350)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF5350)) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}
@Composable
fun ViewAndSortDialog(
    currentViewMode: BrowserViewMode,
    currentLayoutMode: BrowserLayoutMode,
    currentSortOption: SortOption,
    currentSortDirection: SortDirection,
    currentFields: BrowserCardFields,
    accentColor: Color,
    onDismiss: () -> Unit,
    onApply: (BrowserViewMode, BrowserLayoutMode, SortOption, SortDirection, BrowserCardFields) -> Unit
) {
    var selectedView by remember { mutableStateOf(currentViewMode) }
    var selectedLayout by remember { mutableStateOf(currentLayoutMode) }
    var selectedSort by remember { mutableStateOf(currentSortOption) }
    var selectedDir by remember { mutableStateOf(currentSortDirection) }
    var fields by remember { mutableStateOf(currentFields) }
    var fieldsExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text("View & Sort", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // View Mode Row
                Text("View", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    BrowserViewMode.entries.forEach { mode ->
                        val active = selectedView == mode
                        FilterChip(
                            selected = active,
                            onClick = { selectedView = mode },
                            label = { Text(mode.label, fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Layout Row (List / Grid)
                Text("Layout", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BrowserLayoutMode.entries.forEach { layout ->
                        val active = selectedLayout == layout
                        FilterChip(
                            selected = active,
                            onClick = { selectedLayout = layout },
                            leadingIcon = {
                                Icon(
                                    if (layout == BrowserLayoutMode.LIST) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(layout.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Sort Grid (10 MX Player Pro Options)
                Text("Sort by", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(175.dp)
                ) {
                    items(SortOption.entries.size) { idx ->
                        val opt = SortOption.entries[idx]
                        val active = selectedSort == opt
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .clickable { selectedSort = opt }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (active) accentColor else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = opt.label,
                                color = if (active) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Ascending / Descending Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SortDirection.entries.forEach { dir ->
                        val active = selectedDir == dir
                        OutlinedButton(
                            onClick = { selectedDir = dir },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (active) accentColor else Color.LightGray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (dir == SortDirection.ASCENDING) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (dir == SortDirection.ASCENDING) "A to Z / Min" else "Z to A / Max", fontSize = 11.sp)
                        }
                    }
                }

                // Fields Accordion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { fieldsExpanded = !fieldsExpanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fields (Card Badges)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(
                        if (fieldsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                }

                if (fieldsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FieldToggleRow("Resolution (1080p/4K)", fields.showResolution) { fields = fields.copy(showResolution = it) }
                        FieldToggleRow("File Size", fields.showSize) { fields = fields.copy(showSize = it) }
                        FieldToggleRow("Frame Rate (FPS)", fields.showFps) { fields = fields.copy(showFps = it) }
                        FieldToggleRow("File Extension (MKV/MP4)", fields.showExtension) { fields = fields.copy(showExtension = it) }
                        FieldToggleRow("Folder Path", fields.showPath) { fields = fields.copy(showPath = it) }
                        FieldToggleRow("NEW Badge", fields.showNewTag) { fields = fields.copy(showNewTag = it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(selectedView, selectedLayout, selectedSort, selectedDir, fields)
                onDismiss()
            }) {
                Text("Done", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
@Composable
private fun FieldToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun TargetFolderPickerDialog(
    title: String,
    folders: List<File>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectFolder: (File) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var showCreateInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showCreateInput = !showCreateInput }) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create New Folder", color = accentColor)
                }

                if (showCreateInput) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Folder name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val clean = newFolderName.trim()
                                if (clean.isNotEmpty() && folders.isNotEmpty()) {
                                    val baseParent = folders.first().parentFile ?: folders.first()
                                    val created = File(baseParent, clean).apply { mkdirs() }
                                    onSelectFolder(created)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) { Text("OK") }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.take(12).forEach { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    onSelectFolder(dir)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(dir.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(dir.absolutePath, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
@Composable
fun RenameItemDialog(
    title: String,
    initialName: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (textValue.trim().isNotEmpty()) {
                    onConfirm(textValue.trim())
                    onDismiss()
                }
            }) {
                Text("Rename", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun PropertiesInfoDialog(
    title: String,
    details: List<Pair<String, String>>,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.forEach { (key, value) ->
                    Column {
                        Text(key, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = Color.LightGray, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = accentColor) }
        }
    )
}
