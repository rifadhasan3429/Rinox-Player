package com.rinox.videoplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rinox.videoplayer.data.*
import com.rinox.videoplayer.ui.components.FolderItemCard
import com.rinox.videoplayer.ui.components.VideoItemCard
import com.rinox.videoplayer.ui.screens.AccentPalette
import com.rinox.videoplayer.ui.screens.SettingsScreen
import com.rinox.videoplayer.ui.theme.*
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, t -> t.printStackTrace() }
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            var settings by remember { mutableStateOf(settingsManager.loadSettings()) }
            val accentColor = AccentPalette.getOrElse(settings.accentIndex) { RinoxBlue }

            RinoxPlayerTheme(accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = AmoledBlack) {
                    RinoxMainApp(
                        settings = settings,
                        settingsManager = settingsManager,
                        onUpdateSettings = { updated ->
                            settings = updated
                            settingsManager.saveSettings(updated)
                        }
                    )
                }
            }
        }
    }

    private fun enableMaxRefreshRate() {
        try {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
            else @Suppress("DEPRECATION") windowManager.defaultDisplay
            val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate } ?: return
            val params: WindowManager.LayoutParams = window.attributes
            params.preferredDisplayModeId = maxMode.modeId
            window.attributes = params
        } catch (_: Exception) {}
    }
}

private enum class BrowserScreen { FOLDERS, FOLDER_VIDEOS, ALL_VIDEOS, PRIVACY_VAULT, SETTINGS }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RinoxMainApp(
    settings: AppSettings,
    settingsManager: SettingsManager,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context, settingsManager) }
    val accentColor = AccentPalette.getOrElse(settings.accentIndex) { RinoxBlue }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    var allVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var privateVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var currentScreen by remember { mutableStateOf(BrowserScreen.FOLDERS) }
    var selectedFolderPath by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showPinPrompt by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var propertiesVideo by remember { mutableStateOf<VideoItem?>(null) }

    fun reloadVideos() {
        if (!hasPermission) return
        scope.launch {
            val scanned = withContext(Dispatchers.IO) { repository.scanAllVideos() }
            val priv = withContext(Dispatchers.IO) { repository.getPrivateVideos() }
            allVideos = scanned
            privateVideos = priv
        }
    }

    LaunchedEffect(hasPermission) { reloadVideos() }

    BackHandler(enabled = currentScreen != BrowserScreen.FOLDERS || searchActive) {
        when {
            searchActive -> { searchActive = false; searchQuery = "" }
            currentScreen != BrowserScreen.FOLDERS -> currentScreen = BrowserScreen.FOLDERS
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rinox Player", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Grant storage permission to load your offline videos.", color = TextSecondary)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permLauncher.launch(requiredPermission) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) { Text("Grant Storage Access") }
            }
        }
        return
    }

    if (currentScreen == BrowserScreen.SETTINGS) {
        SettingsScreen(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onRescanLibrary = { reloadVideos(); Toast.makeText(context, "Library Rescanned", Toast.LENGTH_SHORT).show() },
            onBack = { currentScreen = BrowserScreen.FOLDERS }
        )
        return
    }
    val sortedVideos = remember(allVideos, sortOption, searchQuery) {
        val filtered = if (searchQuery.isBlank()) allVideos
        else allVideos.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        when (sortOption) {
            SortOption.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortOption.DATE -> filtered.sortedByDescending { it.dateAdded }
            SortOption.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val folders = remember(sortedVideos) {
        sortedVideos.groupBy { it.folderPath }.map { (path, vids) ->
            VideoFolder(
                name = vids.first().folderName,
                path = path,
                videos = vids,
                totalDurationMs = vids.sumOf { it.durationMs },
                newCount = vids.count { it.isNew }
            )
        }.sortedBy { it.name.lowercase() }
    }

    val activeVideoList = when (currentScreen) {
        BrowserScreen.FOLDER_VIDEOS -> sortedVideos.filter { it.folderPath == selectedFolderPath }
        BrowserScreen.ALL_VIDEOS -> sortedVideos
        BrowserScreen.PRIVACY_VAULT -> privateVideos
        else -> emptyList()
    }

    val topTitle = when (currentScreen) {
        BrowserScreen.FOLDERS -> "Rinox Player"
        BrowserScreen.FOLDER_VIDEOS -> folders.find { it.path == selectedFolderPath }?.name ?: "Folder"
        BrowserScreen.ALL_VIDEOS -> "All Videos (${sortedVideos.size})"
        BrowserScreen.PRIVACY_VAULT -> "Privacy Vault (${privateVideos.size})"
        else -> "Rinox Player"
    }

    Scaffold(
        containerColor = AmoledBlack,
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search videos...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(topTitle, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (currentScreen != BrowserScreen.FOLDERS) {
                        IconButton(onClick = { currentScreen = BrowserScreen.FOLDERS }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                        Icon(if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary)
                    }
                    IconButton(onClick = { onUpdateSettings(settings.copy(isGridView = !settings.isGridView)) }) {
                        Icon(
                            if (settings.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Toggle View",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = { currentScreen = BrowserScreen.SETTINGS }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val lastPath = settingsManager.getLastPlayedPath()
                    val target = allVideos.find { it.path == lastPath } ?: allVideos.firstOrNull()
                    if (target != null) {
                        settingsManager.markVideoWatched(target.path)
                        settingsManager.saveLastPlayedPath(target.path)
                        reloadVideos()
                        Toast.makeText(context, "Ready for Phase 3 Player: ${target.displayName}", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = accentColor,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (currentScreen == BrowserScreen.FOLDERS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (settings.privacyPin.isEmpty()) {
                                Toast.makeText(context, "Set 4-Digit PIN in Settings > Media Library first", Toast.LENGTH_LONG).show()
                            } else {
                                pinInput = ""; showPinPrompt = true
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Privacy") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { currentScreen = BrowserScreen.ALL_VIDEOS },
                        label = { Text("All Videos") }
                    )
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { sortMenuOpen = true },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Sort: ${sortOption.label}") }
                        )
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            SortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label, color = TextPrimary) },
                                    onClick = { sortOption = opt; sortMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
            if (currentScreen == BrowserScreen.FOLDERS && searchQuery.isBlank()) {
                if (settings.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(folders, key = { it.path }) { folder ->
                            FolderItemCard(folder = folder, settings = settings, accentColor = accentColor) {
                                selectedFolderPath = folder.path
                                currentScreen = BrowserScreen.FOLDER_VIDEOS
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(folders, key = { it.path }) { folder ->
                            FolderItemCard(folder = folder, settings = settings, accentColor = accentColor) {
                                selectedFolderPath = folder.path
                                currentScreen = BrowserScreen.FOLDER_VIDEOS
                            }
                        }
                    }
                }
            } else {
                val listToShow = if (currentScreen == BrowserScreen.FOLDERS) sortedVideos else activeVideoList
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(listToShow, key = { it.path }) { video ->
                        VideoItemCard(
                            video = video,
                            settings = settings,
                            accentColor = accentColor,
                            onPlayClick = {
                                settingsManager.markVideoWatched(video.path)
                                settingsManager.saveLastPlayedPath(video.path)
                                reloadVideos()
                                Toast.makeText(context, "Ready for Phase 3 Player: ${video.displayName}", Toast.LENGTH_SHORT).show()
                            },
                            onMenuAction = { action ->
                                when (action) {
                                    "properties" -> propertiesVideo = video
                                    "privacy" -> scope.launch(Dispatchers.IO) {
                                        if (video.isPrivate) repository.unhideFromPrivacy(video)
                                        else repository.moveToPrivacy(video)
                                        withContext(Dispatchers.Main) { reloadVideos() }
                                    }
                                    "delete" -> scope.launch(Dispatchers.IO) {
                                        repository.deleteVideo(video)
                                        withContext(Dispatchers.Main) { reloadVideos() }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPinPrompt) {
        AlertDialog(
            onDismissRequest = { showPinPrompt = false },
            containerColor = DarkCard,
            title = { Text("Enter Privacy PIN", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                    label = { Text("4-Digit PIN") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput == settings.privacyPin) {
                        showPinPrompt = false
                        currentScreen = BrowserScreen.PRIVACY_VAULT
                    } else {
                        Toast.makeText(context, "Wrong PIN!", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Unlock", color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showPinPrompt = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    propertiesVideo?.let { v ->
        AlertDialog(
            onDismissRequest = { propertiesVideo = null },
            containerColor = DarkCard,
            title = { Text("Video Properties", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File: ${v.displayName}", color = TextPrimary, fontSize = 13.sp)
                    Text("Resolution: ${v.width} x ${v.height}", color = TextSecondary, fontSize = 13.sp)
                    Text("Frame Rate: ${v.fps} FPS", color = RinoxCyan, fontSize = 13.sp)
                    Text("Size: ${FormatUtils.formatSize(v.sizeBytes)}", color = TextSecondary, fontSize = 13.sp)
                    Text("Duration: ${FormatUtils.formatDuration(v.durationMs)}", color = TextSecondary, fontSize = 13.sp)
                    Text("Path: ${v.path}", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesVideo = null }) { Text("Close", color = accentColor) }
            }
        )
    }
}
