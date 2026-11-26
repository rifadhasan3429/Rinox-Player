package com.rinox.videoplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinox.videoplayer.data.AppSettings
import com.rinox.videoplayer.data.VideoFolder
import com.rinox.videoplayer.data.VideoItem
import com.rinox.videoplayer.ui.theme.*
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable
fun FolderItemCard(
    folder: VideoFolder,
    settings: AppSettings,
    accentColor: Color,
    onClick: () -> Unit
) {
    val subIcon: ImageVector = remember(folder.name) {
        val lower = folder.name.lowercase()
        when {
            lower.contains("camera") || lower.contains("dcim") -> Icons.Rounded.PhotoCamera
            lower.contains("movie") || lower.contains("film") -> Icons.Rounded.Movie
            lower.contains("whatsapp") -> Icons.Rounded.Chat
            lower.contains("download") -> Icons.Rounded.Download
            lower.contains("screen") -> Icons.Rounded.Screenshot
            else -> Icons.Rounded.VideoLibrary
        }
    }

    if (settings.isGridView) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        imageVector = subIcon,
                        contentDescription = null,
                        tint = AmoledBlack,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                            .offset(y = 3.dp)
                    )
                    if (settings.showNewBadge && folder.newCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${folder.newCount}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (settings.showFolderDuration && folder.totalDurationMs > 0L) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xDD000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(folder.totalDurationMs),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = folder.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.videos.size} videos",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = subIcon,
                    contentDescription = null,
                    tint = AmoledBlack,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                        .offset(y = 3.dp)
                )
                if (settings.showNewBadge && folder.newCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${folder.newCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (settings.showFolderDuration && folder.totalDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xDD000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(folder.totalDurationMs),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${folder.videos.size} videos",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
@Composable
fun VideoItemCard(
    video: VideoItem,
    settings: AppSettings,
    accentColor: Color,
    onPlayClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    val context = LocalContext.current
    var thumbBitmap by remember(video.path) { mutableStateOf<Bitmap?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(video.path) {
        thumbBitmap = withContext(Dispatchers.IO) {
            FormatUtils.getVideoThumbnail(context, video.path)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(116.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkCard)
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                )
            }
            if (settings.showNewBadge && video.isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (video.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.durationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.displayName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (settings.showSubtitleBadge && video.subtitleTypes.isNotEmpty()) {
                    video.subtitleTypes.forEach { sub ->
                        BadgePill(text = sub, bgColor = Color(0xFF1B5E20), textColor = Color(0xFF81C784))
                    }
                }
                if (settings.showVideoSize) {
                    BadgePill(
                        text = FormatUtils.formatSize(video.sizeBytes),
                        bgColor = DarkCard,
                        textColor = accentColor
                    )
                }
                if (settings.showVideoFps) {
                    BadgePill(
                        text = "${video.fps} FPS",
                        bgColor = DarkCard,
                        textColor = RinoxCyan
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Properties", color = TextPrimary) },
                    onClick = { menuExpanded = false; onMenuAction("properties") }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (video.isPrivate) "Unhide from Privacy" else "Move to Privacy",
                            color = TextPrimary
                        )
                    },
                    onClick = { menuExpanded = false; onMenuAction("privacy") }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF5350)) },
                    onClick = { menuExpanded = false; onMenuAction("delete") }
                )
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
