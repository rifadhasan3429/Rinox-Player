package com.rinox.videoplayer.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinox.videoplayer.data.*
import com.rinox.videoplayer.ui.theme.DarkCard
import com.rinox.videoplayer.ui.theme.RinoxBlue
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
@Composable
fun CustomFolderVectorIcon(
    folderName: String,
    badgeCount: Int,
    modifier: Modifier = Modifier
) {
    val lower = folderName.lowercase()
    val overlayIcon = when {
        lower.contains("camera") || lower.contains("dcim") -> Icons.Rounded.PhotoCamera
        lower.contains("download") -> Icons.Rounded.ArrowDownward
        lower.contains("movie") || lower.contains("video") || lower.contains("film") -> Icons.Rounded.Movie
        lower.contains("screenshot") -> Icons.Rounded.Smartphone
        lower.contains("whatsapp") || lower.contains("messenger") || lower.contains("telegram") -> Icons.Rounded.Forum
        else -> null
    }

    Box(modifier = modifier.size(62.dp)) {
        Canvas(
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.BottomStart)
        ) {
            val w = size.width
            val h = size.height

            val tabPath = Path().apply {
                moveTo(w * 0.08f, h * 0.24f)
                lineTo(w * 0.08f, h * 0.14f)
                quadraticBezierTo(w * 0.08f, h * 0.08f, w * 0.16f, h * 0.08f)
                lineTo(w * 0.40f, h * 0.08f)
                lineTo(w * 0.48f, h * 0.18f)
                lineTo(w * 0.88f, h * 0.18f)
                quadraticBezierTo(w * 0.94f, h * 0.18f, w * 0.94f, h * 0.26f)
                lineTo(w * 0.94f, h * 0.40f)
                lineTo(w * 0.08f, h * 0.40f)
                close()
            }
            drawPath(path = tabPath, color = Color(0xFF1976D2))

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF42A5F5), Color(0xFF2196F3))
                ),
                topLeft = Offset(w * 0.06f, h * 0.22f),
                size = ComposeSize(w * 0.90f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
            )
        }

        if (overlayIcon != null) {
            Icon(
                imageVector = overlayIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center)
                    .offset(x = (-3).dp, y = 6.dp)
            )
        }

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
    showFolderDuration: Boolean = false,
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
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val subColor = if (isDark) Color(0xFF9AA3B2) else Color(0xFF5A6270)
    val menuBg = if (isDark) DarkCard else Color.White
    val unwatchedCount = remember(folder.videos) { folder.videos.count { !it.isWatched } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomFolderVectorIcon(
            folderName = folder.name,
            badgeCount = unwatchedCount
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            val subText = if (showFolderDuration && folder.totalDurationMs > 0L) {
                "${folder.videoCount} videos • ${FormatUtils.formatDuration(folder.totalDurationMs)}"
            } else {
                "${folder.videoCount} videos"
            }
            Text(
                text = subText,
                color = subColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Folder Options", tint = subColor.copy(alpha = 0.75f))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(menuBg)
            ) {
                DropdownMenuItem(
                    text = { Text("Play All", color = titleColor) },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = accentColor) },
                    onClick = { menuExpanded = false; onPlayAll() }
                )
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = titleColor) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = titleColor) },
                    onClick = { menuExpanded = false; onRenameFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Copy Folder to...", color = titleColor) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = titleColor) },
                    onClick = { menuExpanded = false; onCopyFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Move Folder to...", color = titleColor) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = titleColor) },
                    onClick = { menuExpanded = false; onMoveFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Hide Folder", color = titleColor) },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, tint = titleColor) },
                    onClick = { menuExpanded = false; onHideFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Properties", color = titleColor) },
                    leadingIcon = { Icon(Icons.Rounded.Info, null, tint = titleColor) },
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
                        isDark = isDark,
                        accentColor = accentColor,
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
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnailBox(
                video = video,
                accentColor = accentColor,
                modifier = Modifier.size(width = 116.dp, height = 66.dp)
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
                                .background(RinoxBlue.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("NEW", color = RinoxBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    isDark = isDark,
                    accentColor = accentColor,
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
    isDark: Boolean,
    accentColor: Color,
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
    val menuBg = if (isDark) DarkCard else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF111318)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(menuBg)
    ) {
        DropdownMenuItem(
            text = { Text("Play", color = textColor) },
            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = accentColor) },
            onClick = { onDismiss(); onPlay() }
        )
        DropdownMenuItem(
            text = { Text("Rename", color = textColor) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = textColor) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Copy to...", color = textColor) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = textColor) },
            onClick = { onDismiss(); onCopyTo() }
        )
        DropdownMenuItem(
            text = { Text("Move to...", color = textColor) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = textColor) },
            onClick = { onDismiss(); onMoveTo() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (isPrivate) "Unhide from Privacy" else "Move to Privacy",
                    color = textColor
                )
            },
            leadingIcon = {
                Icon(
                    if (isPrivate) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    null,
                    tint = accentColor
                )
            },
            onClick = { onDismiss(); onTogglePrivacy() }
        )
        DropdownMenuItem(
            text = { Text("Share", color = textColor) },
            leadingIcon = { Icon(Icons.Rounded.Share, null, tint = textColor) },
            onClick = { onDismiss(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("Properties", color = textColor) },
            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = textColor) },
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
    isDark: Boolean,
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

    val dialogBg = if (isDark) DarkCard else Color.White
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val subColor = if (isDark) Color.LightGray else Color(0xFF5A6270)
    val itemBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF1F4F9)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = {
            Text("View & Sort", color = titleColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("View", color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    BrowserViewMode.entries.forEach { mode ->
                        val active = selectedView == mode
                        FilterChip(
                            selected = active,
                            onClick = { selectedView = mode },
                            label = { Text(mode.label, fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                selectedLabelColor = accentColor,
                                labelColor = titleColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Layout", color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                selectedLabelColor = accentColor,
                                labelColor = titleColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("Sort by", color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                .background(if (active) accentColor.copy(alpha = 0.2f) else itemBg)
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
                                color = if (active) accentColor else titleColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SortDirection.entries.forEach { dir ->
                        val active = selectedDir == dir
                        OutlinedButton(
                            onClick = { selectedDir = dir },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                                contentColor = if (active) accentColor else subColor
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .clickable { fieldsExpanded = !fieldsExpanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fields (Card Badges)", color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(
                        if (fieldsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = subColor
                    )
                }

                if (fieldsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldToggleRow("Resolution (1080p/4K)", fields.showResolution, titleColor) { fields = fields.copy(showResolution = it) }
                        FieldToggleRow("File Size", fields.showSize, titleColor) { fields = fields.copy(showSize = it) }
                        FieldToggleRow("Frame Rate (FPS)", fields.showFps, titleColor) { fields = fields.copy(showFps = it) }
                        FieldToggleRow("File Extension (MKV/MP4)", fields.showExtension, titleColor) { fields = fields.copy(showExtension = it) }
                        FieldToggleRow("Folder Path", fields.showPath, titleColor) { fields = fields.copy(showPath = it) }
                        FieldToggleRow("NEW Badge", fields.showNewTag, titleColor) { fields = fields.copy(showNewTag = it) }
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
private fun FieldToggleRow(label: String, checked: Boolean, textColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = textColor, fontSize = 13.sp)
    }
}

@Composable
fun TargetFolderPickerDialog(
    title: String,
    folders: List<File>,
    isDark: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectFolder: (File) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var showCreateInput by remember { mutableStateOf(false) }

    val dialogBg = if (isDark) DarkCard else Color.White
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val itemBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF1F4F9)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = { Text(title, color = titleColor, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                folders.take(15).forEach { dir ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(itemBg)
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
                            Text(dir.name, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(dir.absolutePath, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    isDark: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialName) }
    val dialogBg = if (isDark) DarkCard else Color.White
    val titleColor = if (isDark) Color.White else Color(0xFF111318)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = { Text(title, color = titleColor, fontWeight = FontWeight.Bold) },
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
    isDark: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDark) DarkCard else Color.White
    val titleColor = if (isDark) Color.White else Color(0xFF111318)
    val valColor = if (isDark) Color.LightGray else Color(0xFF3E4552)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = { Text(title, color = titleColor, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.forEach { (key, value) ->
                    Column {
                        Text(key, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = valColor, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = accentColor) }
        }
    )
}
