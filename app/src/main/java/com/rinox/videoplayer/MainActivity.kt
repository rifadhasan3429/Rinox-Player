package com.rinox.videoplayer

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rinox.videoplayer.data.*
import com.rinox.videoplayer.ui.components.FolderItemCard
import com.rinox.videoplayer.ui.components.VideoItemCard
import com.rinox.videoplayer.ui.screens.AccentPalette
import com.rinox.videoplayer.ui.screens.SettingsScreen
import com.rinox.videoplayer.ui.screens.VideoPlayerScreen
import com.rinox.videoplayer.ui.theme.*
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class MainActivity : ComponentActivity() {
    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, t -> t.printStackTrace() }
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            var settings by remember { mutableStateOf(settingsManager.loadSettings()) }
            val accentColor = AccentPalette.getOrElse(settings.accentIndex) { RinoxBlue }

            // Dynamic Status Bar & Navigation Bar Icon Contrast Fix
            LaunchedEffect(settings.isDarkTheme) {
                if (settings.isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    )
                }
            }

            RinoxPlayerTheme(darkTheme = settings.isDarkTheme, accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RinoxMainApp(
                        settings = settings,
                        settingsManager = settingsManager,
                        isInPipMode = isInPipMode,
                        onUpdateSettings = { updated ->
                            settings = updated
                            settingsManager.saveSettings(updated)
                        }
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
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
    isInPipMode: Boolean,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context, settingsManager) }
    val accentColor = AccentPalette.getOrElse(settings.accentIndex) { RinoxBlue }
    val bgMain = MaterialTheme.colorScheme.background
    val textMain = MaterialTheme.colorScheme.onBackground
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

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

    var allVideos by remember { mutableStateOf(repository.getInstantCachedVideos()) }
    var privateVideos by remember { mutableStateOf(repository.getInstantCachedPrivate()) }
    var currentScreen by remember { mutableStateOf(BrowserScreen.FOLDERS) }
    var selectedFolderPath by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showPinPrompt by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var propertiesVideo by remember { mutableStateOf<VideoItem?>(null) }

    var activePlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var activePlayIndex by remember { mutableIntStateOf(-1) }

    // Flagship Animated Signature State
    var showCreatorBadge by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(180L)
        showCreatorBadge = true
        delay(2800L)
        showCreatorBadge = false
    }

    fun reloadVideos(force: Boolean = false) {
        if (!hasPermission) return
        scope.launch {
            val scanned = withContext(Dispatchers.IO) { repository.scanAllVideos(forceRefresh = force) }
            val priv = withContext(Dispatchers.IO) { repository.getPrivateVideos() }
            allVideos = scanned
            privateVideos = priv
        }
    }

    LaunchedEffect(hasPermission) {
        if (allVideos.isEmpty()) reloadVideos(force = false)
    }

    if (activePlaylist.isNotEmpty() && activePlayIndex >= 0) {
        VideoPlayerScreen(
            playlist = activePlaylist,
            initialIndex = activePlayIndex,
            settings = settings,
            settingsManager = settingsManager,
            accentColor = accentColor,
            isInPipMode = isInPipMode,
            onClose = {
                activePlaylist = emptyList()
                activePlayIndex = -1
                reloadVideos(force = false)
            }
        )
        return
    }
    BackHandler(enabled = currentScreen != BrowserScreen.FOLDERS || searchActive) {
        when {
            searchActive -> { searchActive = false; searchQuery = "" }
            currentScreen != BrowserScreen.FOLDERS -> currentScreen = BrowserScreen.FOLDERS
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rinox Player", color = textMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Grant storage permission to load your offline videos.", color = textSub)
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
            onRescanLibrary = { reloadVideos(force = true); Toast.makeText(context, "Library Rescanned", Toast.LENGTH_SHORT).show() },
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
        BrowserScreen.FOLDERS -> "Folders"
        BrowserScreen.FOLDER_VIDEOS -> folders.find { it.path == selectedFolderPath }?.name ?: "Folder"
        BrowserScreen.ALL_VIDEOS -> "All Videos (${sortedVideos.size})"
        BrowserScreen.PRIVACY_VAULT -> "Privacy Vault (${privateVideos.size})"
        else -> "Folders"
    }

    Scaffold(
        containerColor = bgMain,
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
                        Column {
                            Text(
                                text = topTitle,
                                color = textMain,
                                fontSize = if (topTitle.length > 18) 16.sp else 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            AnimatedVisibility(
                                visible = showCreatorBadge && currentScreen == BrowserScreen.FOLDERS,
                                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 } + expandHorizontally(tween(600)),
                                exit = fadeOut(tween(700))
                            ) {
                                ShimmerCreatorTag(accentColor = accentColor)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (currentScreen != BrowserScreen.FOLDERS) {
                        IconButton(onClick = { currentScreen = BrowserScreen.FOLDERS }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textMain)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                        Icon(if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search", tint = textMain, modifier = Modifier.size(21.dp))
                    }
                    if (currentScreen == BrowserScreen.FOLDERS) {
                        IconButton(onClick = { onUpdateSettings(settings.copy(isDarkTheme = !settings.isDarkTheme)) }) {
                            Icon(
                                if (settings.isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Theme",
                                tint = textMain,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onUpdateSettings(settings.copy(isGridView = !settings.isGridView)) }) {
                            Icon(
                                if (settings.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = "Toggle View",
                                tint = textMain,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = { currentScreen = BrowserScreen.SETTINGS }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = textMain, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgMain)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val lastPath = settingsManager.getLastPlayedPath()
                    val idx = sortedVideos.indexOfFirst { it.path == lastPath }
                    if (sortedVideos.isNotEmpty()) {
                        activePlaylist = sortedVideos
                        activePlayIndex = if (idx >= 0) idx else 0
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SleekPillChip(
                        icon = Icons.Rounded.Lock,
                        label = "Privacy",
                        onClick = {
                            if (settings.privacyPin.isEmpty()) {
                                Toast.makeText(context, "Set 4-Digit PIN in Settings > Media Library first", Toast.LENGTH_LONG).show()
                            } else {
                                pinInput = ""; showPinPrompt = true
                            }
                        }
                    )
                    SleekPillChip(
                        icon = Icons.Rounded.VideoLibrary,
                        label = "All Videos",
                        onClick = { currentScreen = BrowserScreen.ALL_VIDEOS }
                    )
                    Box {
                        SleekPillChip(
                            icon = Icons.AutoMirrored.Rounded.Sort,
                            label = sortOption.label,
                            onClick = { sortMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            SortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = { sortOption = opt; sortMenuOpen = false }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
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
                    items(listToShow.size) { index ->
                        val video = listToShow[index]
                        VideoItemCard(
                            video = video,
                            settings = settings,
                            accentColor = accentColor,
                            onPlayClick = {
                                activePlaylist = listToShow
                                activePlayIndex = index
                            },
                            onMenuAction = { action ->
                                when (action) {
                                    "properties" -> propertiesVideo = video
                                    "privacy" -> scope.launch(Dispatchers.IO) {
                                        if (video.isPrivate) repository.unhideFromPrivacy(video)
                                        else repository.moveToPrivacy(video)
                                        withContext(Dispatchers.Main) { reloadVideos(force = true) }
                                    }
                                    "delete" -> scope.launch(Dispatchers.IO) {
                                        repository.deleteVideo(video)
                                        withContext(Dispatchers.Main) { reloadVideos(force = true) }
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
            title = { Text("Enter Privacy PIN") },
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
                TextButton(onClick = { showPinPrompt = false }) { Text("Cancel") }
            }
        )
    }

    propertiesVideo?.let { v ->
        AlertDialog(
            onDismissRequest = { propertiesVideo = null },
            title = { Text("Video Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File: ${v.displayName}", fontSize = 13.sp)
                    Text("Resolution: ${v.width} x ${v.height}", fontSize = 13.sp)
                    Text("Frame Rate: ${v.fps} FPS", color = accentColor, fontSize = 13.sp)
                    Text("Size: ${FormatUtils.formatSize(v.sizeBytes)}", fontSize = 13.sp)
                    Text("Duration: ${FormatUtils.formatDuration(v.durationMs)}", fontSize = 13.sp)
                    Text("Path: ${v.path}", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesVideo = null }) { Text("Close", color = accentColor) }
            }
        )
    }
}

@Composable
private fun ShimmerCreatorTag(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -150f,
        targetValue = 450f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerShift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(accentColor, Color.White, RinoxCyan, accentColor),
        start = Offset(shift, 0f),
        end = Offset(shift + 140f, 30f)
    )
    Text(
        text = "Crafted by Rifad Hasan",
        style = TextStyle(
            brush = brush,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    )
}

@Composable
private fun SleekPillChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
