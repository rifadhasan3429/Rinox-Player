package com.rinox.videoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinox.videoplayer.data.AppSettings
import com.rinox.videoplayer.ui.theme.*

val AccentPalette = listOf(
    Color(0xFF2196F3),
    Color(0xFF00E5FF),
    Color(0xFF00E676),
    Color(0xFFFF4081),
    Color(0xFFFF9100),
    Color(0xFFB388FF)
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onRescanLibrary: () -> Unit,
    onBack: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val bgMain = MaterialTheme.colorScheme.background
    val textMain = MaterialTheme.colorScheme.onBackground
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant
    val cardSurface = MaterialTheme.colorScheme.surfaceVariant

    Scaffold(
        containerColor = bgMain,
        topBar = {
            TopAppBar(
                title = { Text("Pro Settings", color = textMain, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textMain)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgMain)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsCategoryCard(title = "1. Appearance", icon = Icons.Rounded.Palette) {
                    SettingSwitchRow(
                        title = "AMOLED Dark Mode",
                        subtitle = "Turn off for clean Flagship Light (White) theme",
                        checked = settings.isDarkTheme,
                        onCheckedChange = { onUpdateSettings(settings.copy(isDarkTheme = it)) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Accent Theme Color", color = textSub, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentPalette.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (settings.accentIndex == idx) 3.dp else 0.dp,
                                        color = textMain,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdateSettings(settings.copy(accentIndex = idx)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Show Video Size (MB/GB)",
                        subtitle = "Display file size under video title",
                        checked = settings.showVideoSize,
                        onCheckedChange = { onUpdateSettings(settings.copy(showVideoSize = it)) }
                    )
                    SettingSwitchRow(
                        title = "Show Video FPS (30/60 FPS)",
                        subtitle = "Display frame rate badge under video title",
                        checked = settings.showVideoFps,
                        onCheckedChange = { onUpdateSettings(settings.copy(showVideoFps = it)) }
                    )
                    SettingSwitchRow(
                        title = "Show Folder Total Duration",
                        subtitle = "Show combined duration pill on folder icon",
                        checked = settings.showFolderDuration,
                        onCheckedChange = { onUpdateSettings(settings.copy(showFolderDuration = it)) }
                    )
                    SettingSwitchRow(
                        title = "Show NEW Video Tag",
                        checked = settings.showNewBadge,
                        onCheckedChange = { onUpdateSettings(settings.copy(showNewBadge = it)) }
                    )
                    SettingSwitchRow(
                        title = "Show Subtitle Badges (SRT/ASS)",
                        checked = settings.showSubtitleBadge,
                        onCheckedChange = { onUpdateSettings(settings.copy(showSubtitleBadge = it)) }
                    )
                }
            }

            item {
                SettingsCategoryCard(title = "2. Media Library & Privacy", icon = Icons.Rounded.Lock) {
                    Button(
                        onClick = onRescanLibrary,
                        colors = ButtonDefaults.buttonColors(containerColor = cardSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = RinoxBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-scan Storage Library", color = textMain)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { pinInput = ""; showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = cardSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Pin, contentDescription = null, tint = RinoxBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (settings.privacyPin.isEmpty()) "Set Privacy Vault 4-Digit PIN"
                            else "Reset Privacy Vault PIN",
                            color = textMain
                        )
                    }
                }
            }
            item {
                SettingsCategoryCard(title = "3. Player Controls", icon = Icons.Rounded.PlayCircle) {
                    Text("Tap-to-Seek Step: ${settings.seekStepSeconds}s", color = textMain, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = settings.seekStepSeconds == sec,
                                onClick = { onUpdateSettings(settings.copy(seekStepSeconds = sec)) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                    SettingSwitchRow(
                        title = "Vertical Edge Swipes",
                        subtitle = "Left edge Brightness, Right edge Volume",
                        checked = settings.enableEdgeSwipes,
                        onCheckedChange = { onUpdateSettings(settings.copy(enableEdgeSwipes = it)) }
                    )
                    SettingSwitchRow(
                        title = "Resume Playback",
                        subtitle = "Continue from last watched position",
                        checked = settings.resumePlayback,
                        onCheckedChange = { onUpdateSettings(settings.copy(resumePlayback = it)) }
                    )
                }
            }

            item {
                SettingsCategoryCard(title = "4. Hybrid Decoder Engine", icon = Icons.Rounded.Memory) {
                    Text("Default Decoder Priority: ${settings.defaultDecoder}", color = textMain, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HW", "HW+", "SW").forEach { mode ->
                            FilterChip(
                                selected = settings.defaultDecoder == mode,
                                onClick = { onUpdateSettings(settings.copy(defaultDecoder = mode)) },
                                label = { Text(mode) }
                            )
                        }
                    }
                    SettingSwitchRow(
                        title = "Smooth Performance Mode",
                        subtitle = "Optimize frame-pacing for mid-range devices",
                        checked = settings.smoothPerformanceMode,
                        onCheckedChange = { onUpdateSettings(settings.copy(smoothPerformanceMode = it)) }
                    )
                }
            }

            item {
                SettingsCategoryCard(title = "5. Audio & Headphone", icon = Icons.Rounded.Headphones) {
                    SettingSwitchRow(
                        title = "Auto-Pause on Headphone Unplug",
                        checked = settings.pauseOnUnplug,
                        onCheckedChange = { onUpdateSettings(settings.copy(pauseOnUnplug = it)) }
                    )
                    SettingSwitchRow(
                        title = "Auto Headphone Background Mode",
                        checked = settings.autoHeadphonePlay,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoHeadphonePlay = it)) }
                    )
                }
            }

            item {
                SettingsCategoryCard(title = "6. Subtitle Engine", icon = Icons.Rounded.Subtitles) {
                    SettingSwitchRow(
                        title = "Auto-Load Matching Subtitles",
                        subtitle = "Load .srt, .ass, .ssa, .vtt automatically",
                        checked = settings.autoLoadSubtitles,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoLoadSubtitles = it)) }
                    )
                    Text("Subtitle Font Size: ${settings.subFontSize}sp", color = textMain, fontSize = 14.sp)
                    Slider(
                        value = settings.subFontSize.toFloat(),
                        onValueChange = { onUpdateSettings(settings.copy(subFontSize = it.toInt())) },
                        valueRange = 12f..32f
                    )
                }
            }

            item {
                SettingsCategoryCard(title = "7. About Rinox Player", icon = Icons.Rounded.Info) {
                    Text("Rinox Player v1.0.0 (64-Bit ARM64)", color = textMain, fontWeight = FontWeight.SemiBold)
                    Text("100% Offline • Zero-Load Engine • AMOLED & Light", color = textSub, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set 4-Digit Privacy PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
                    label = { Text("Enter 4 digits") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length == 4) {
                            onUpdateSettings(settings.copy(privacyPin = pinInput))
                            showPinDialog = false
                        }
                    }
                ) { Text("Save PIN", color = RinoxBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = RinoxBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
