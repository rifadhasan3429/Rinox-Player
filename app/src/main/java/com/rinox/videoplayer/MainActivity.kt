package com.rinox.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rinox.videoplayer.data.*
import com.rinox.videoplayer.ui.components.*
import com.rinox.videoplayer.ui.screens.SettingsScreen
import com.rinox.videoplayer.ui.screens.VideoPlayerScreen
import com.rinox.videoplayer.ui.theme.*
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.launch
import java.io.File
class MainActivity : ComponentActivity() {

    private var isInPipModeState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val modes = display?.supportedModes
                val maxMode = modes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            } catch (_: Exception) {}
        }

        val externalUri: Uri? = intent?.data

        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            var settings by remember { mutableStateOf(settingsManager.loadSettings()) }

            val isDark = settings.isDarkTheme
            val accentColor = when (settings.accentIndex) {
                1 -> RinoxPurple
                2 -> RinoxEmerald
                3 -> RinoxAmber
                4 -> RinoxCrimson
                else -> RinoxCyan
            }

            RinoxPlayerTheme(darkTheme = isDark, accentColor = accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDark) DarkBg else LightBg
                ) {
                    RinoxAppContent(
                        settingsManager = settingsManager,
                        settings = settings,
                        onSettingsUpdated = { updated ->
                            settings = updated
                            settingsManager.saveSettings(updated)
                        },
                        isDark = isDark,
                        accentColor = accentColor,
                        isInPipMode = isInPipModeState,
                        initialExternalUri = externalUri
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }
}
@Composable
fun RinoxAppContent(
    settingsManager: SettingsManager,
    settings: AppSettings,
    onSettingsUpdated: (AppSettings) -> Unit,
    isDark: Boolean,
    accentColor: Color,
    isInPipMode: Boolean,
    initialExternalUri: Uri?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context) }

    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = checkStoragePermission(context)
    }

    var viewMode by remember { mutableStateOf(BrowserViewMode.ALL_FOLDERS) }
    var layoutMode by remember {
        mutableStateOf(if (settings.isGridView) BrowserLayoutMode.GRID else BrowserLayoutMode.LIST)
    }
    var sortOption by remember { mutableStateOf(SortOption.TITLE) }
    var sortDirection by remember { mutableStateOf(SortDirection.ASCENDING) }
    var cardFields by remember {
        mutableStateOf(
            BrowserCardFields(
                showSize = settings.showVideoSize,
                showFps = settings.showVideoFps,
                showNewTag = settings.showNewBadge
            )
        )
    }
    var showViewSortDialog by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var openedFolder by remember { mutableStateOf<VideoFolder?>(null) }

    var folders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var allVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var privateVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var activePlaylist by remember { mutableStateOf<List<VideoItem>?>(null) }
    var activeIndex by remember { mutableIntStateOf(0) }

    var isPrivacyUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var videoToRename by remember { mutableStateOf<VideoItem?>(null) }
    var videoToCopy by remember { mutableStateOf<VideoItem?>(null) }
    var videoToMove by remember { mutableStateOf<VideoItem?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoProperties by remember { mutableStateOf<VideoItem?>(null) }

    var folderToRename by remember { mutableStateOf<VideoFolder?>(null) }
    var folderToCopy by remember { mutableStateOf<VideoFolder?>(null) }
    var folderToMove by remember { mutableStateOf<VideoFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<VideoFolder?>(null) }
    var folderProperties by remember { mutableStateOf<VideoFolder?>(null) }
    var availableTargetDirs by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshMedia() {
        if (!hasPermission) return
        scope.launch {
            if (folders.isEmpty() && allVideos.isEmpty()) isLoading = true
            val (loadedFolders, loadedVideos) = repository.loadAllMedia(
                settings = settings,
                settingsManager = settingsManager,
                viewMode = viewMode,
                sortOption = sortOption,
                sortDirection = sortDirection
            )
            folders = loadedFolders
            allVideos = loadedVideos
            if (openedFolder != null) {
                openedFolder = loadedFolders.find { it.path == openedFolder?.path }
            }
            privateVideos = repository.loadPrivateVideos()
            isLoading = false
        }
    }

    LaunchedEffect(initialExternalUri) {
        if (initialExternalUri != null) {
            val extItem = VideoItem(
                id = -1L,
                title = initialExternalUri.lastPathSegment ?: "External Video",
                displayName = initialExternalUri.lastPathSegment ?: "External Video",
                path = initialExternalUri.path ?: "",
                uri = initialExternalUri,
                folderName = "External",
                folderPath = "",
                durationMs = 0L,
                sizeBytes = 0L,
                width = 1920,
                height = 1080,
                dateAddedSec = System.currentTimeMillis() / 1000L,
                mimeType = "video/*"
            )
            activePlaylist = listOf(extItem)
            activeIndex = 0
        }
    }

    LaunchedEffect(hasPermission, settings, viewMode, sortOption, sortDirection) {
        if (hasPermission) {
            refreshMedia()
        }
    }
    if (activePlaylist != null) {
        VideoPlayerScreen(
            playlist = activePlaylist!!,
            initialIndex = activeIndex,
            settings = settings,
            settingsManager = settingsManager,
            accentColor = accentColor,
            isInPipMode = isInPipMode,
            onClose = {
                activePlaylist = null
                refreshMedia()
            }
        )
        return
    }

    BackHandler(enabled = openedFolder != null || isSearchActive) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (openedFolder != null) {
            openedFolder = null
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = accentColor, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Storage Access Required",
                color = if (isDark) Color.White else Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Allow Rinox Player to scan, play, rename, copy, and organize your videos smoothly.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    permLauncher.launch(perms)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Grant Storage Permission", fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    val textColor = if (isDark) Color.White else Color(0xFF111318)
    val subTextColor = if (isDark) Color(0xFF9AA3B2) else Color(0xFF5A6270)

    Scaffold(
        containerColor = if (isDark) DarkBg else LightBg,
        topBar = {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(if (isDark) DarkCard else Color(0xFFECEFF4))
                                .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Search", tint = textColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search videos or folders...", color = subTextColor, fontSize = 14.sp)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = textColor, fontSize = 14.sp),
                                    cursorBrush = SolidColor(accentColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = subTextColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (openedFolder != null) {
                                IconButton(onClick = { openedFolder = null }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textColor)
                                }
                                Text(
                                    text = openedFolder!!.name,
                                    color = textColor,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Text(
                                    text = "Rinox Player",
                                    color = textColor,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = textColor)
                            }
                            IconButton(onClick = { showViewSortDialog = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "View & Sort", tint = textColor)
                            }
                            IconButton(onClick = { refreshMedia() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = textColor)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) DarkCard else Color.White,
                tonalElevation = 4.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = "Videos") },
                    label = { Text("Videos") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = accentColor, selectedTextColor = accentColor)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Lock, contentDescription = "Privacy") },
                    label = { Text("Privacy") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = accentColor, selectedTextColor = accentColor)
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = accentColor, selectedTextColor = accentColor)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    val query = searchQuery.trim()
                    val filteredFolders = remember(folders, query) {
                        if (query.isEmpty()) folders
                        else folders.filter { it.name.contains(query, ignoreCase = true) }
                    }
                    val filteredVideos = remember(allVideos, openedFolder, query) {
                        val baseList = openedFolder?.videos ?: allVideos
                        if (query.isEmpty()) baseList
                        else baseList.filter { it.displayName.contains(query, ignoreCase = true) }
                    }

                    if (isLoading && folders.isEmpty() && allVideos.isEmpty()) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.align(Alignment.Center))
                    } else if (openedFolder != null || viewMode == BrowserViewMode.FILES || query.isNotEmpty()) {
                        if (filteredVideos.isEmpty()) {
                            Text("No videos found", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        } else if (layoutMode == BrowserLayoutMode.GRID) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredVideos, key = { it.path }) { video ->
                                    val idx = filteredVideos.indexOf(video)
                                    VideoCardItem(
                                        video = video,
                                        isDark = isDark,
                                        accentColor = accentColor,
                                        fields = cardFields,
                                        isGrid = true,
                                        onClick = { activePlaylist = filteredVideos; activeIndex = idx },
                                        onPlay = { activePlaylist = filteredVideos; activeIndex = idx },
                                        onRename = { videoToRename = video },
                                        onCopyTo = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                videoToCopy = video
                                            }
                                        },
                                        onMoveTo = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                videoToMove = video
                                            }
                                        },
                                        onTogglePrivacy = {
                                            scope.launch {
                                                if (repository.moveToPrivateVault(video)) {
                                                    Toast.makeText(context, "Moved to Privacy Vault", Toast.LENGTH_SHORT).show()
                                                    refreshMedia()
                                                }
                                            }
                                        },
                                        onShare = { shareVideoFile(context, video) },
                                        onProperties = { videoProperties = video },
                                        onDelete = { videoToDelete = video }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredVideos, key = { it.path }) { video ->
                                    val idx = filteredVideos.indexOf(video)
                                    VideoCardItem(
                                        video = video,
                                        isDark = isDark,
                                        accentColor = accentColor,
                                        fields = cardFields,
                                        isGrid = false,
                                        onClick = { activePlaylist = filteredVideos; activeIndex = idx },
                                        onPlay = { activePlaylist = filteredVideos; activeIndex = idx },
                                        onRename = { videoToRename = video },
                                        onCopyTo = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                videoToCopy = video
                                            }
                                        },
                                        onMoveTo = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                videoToMove = video
                                            }
                                        },
                                        onTogglePrivacy = {
                                            scope.launch {
                                                if (repository.moveToPrivateVault(video)) {
                                                    Toast.makeText(context, "Moved to Privacy Vault", Toast.LENGTH_SHORT).show()
                                                    refreshMedia()
                                                }
                                            }
                                        },
                                        onShare = { shareVideoFile(context, video) },
                                        onProperties = { videoProperties = video },
                                        onDelete = { videoToDelete = video }
                                    )
                                }
                            }
                        }
                    } else {
                        if (filteredFolders.isEmpty()) {
                            Text("No video folders found", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredFolders, key = { it.path }) { folder ->
                                    FolderCardItem(
                                        folder = folder,
                                        isDark = isDark,
                                        accentColor = accentColor,
                                        onClick = { openedFolder = folder },
                                        onPlayAll = {
                                            if (folder.videos.isNotEmpty()) {
                                                activePlaylist = folder.videos
                                                activeIndex = 0
                                            }
                                        },
                                        onRenameFolder = { folderToRename = folder },
                                        onCopyFolder = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                folderToCopy = folder
                                            }
                                        },
                                        onMoveFolder = {
                                            scope.launch {
                                                availableTargetDirs = repository.getAvailableTargetFolders(folders)
                                                folderToMove = folder
                                            }
                                        },
                                        onHideFolder = {
                                            settingsManager.toggleExcludedFolder(folder.path)
                                            Toast.makeText(context, "Folder hidden", Toast.LENGTH_SHORT).show()
                                            refreshMedia()
                                        },
                                        onProperties = { folderProperties = folder },
                                        onDeleteFolder = { folderToDelete = folder }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (settings.privacyPin.isNotEmpty() && !isPrivacyUnlocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = accentColor, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Enter Privacy PIN", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it
                                    if (it == settings.privacyPin) {
                                        isPrivacyUnlocked = true
                                        pinInput = ""
                                    }
                                },
                                singleLine = true,
                                placeholder = { Text("4-digit PIN") }
                            )
                        }
                    } else {
                        if (privateVideos.isEmpty()) {
                            Text("Privacy Vault is empty", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(privateVideos, key = { it.path }) { pVid ->
                                    val idx = privateVideos.indexOf(pVid)
                                    VideoCardItem(
                                        video = pVid,
                                        isDark = isDark,
                                        accentColor = accentColor,
                                        fields = cardFields,
                                        onClick = { activePlaylist = privateVideos; activeIndex = idx },
                                        onPlay = { activePlaylist = privateVideos; activeIndex = idx },
                                        onRename = { videoToRename = pVid },
                                        onCopyTo = {},
                                        onMoveTo = {},
                                        onTogglePrivacy = {
                                            scope.launch {
                                                if (repository.unhideFromPrivateVault(pVid)) {
                                                    Toast.makeText(context, "Unhidden to Gallery", Toast.LENGTH_SHORT).show()
                                                    refreshMedia()
                                                }
                                            }
                                        },
                                        onShare = { shareVideoFile(context, pVid) },
                                        onProperties = { videoProperties = pVid },
                                        onDelete = { videoToDelete = pVid }
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    SettingsScreen(
                        settings = settings,
                        settingsManager = settingsManager,
                        accentColor = accentColor,
                        onUpdateSettings = onSettingsUpdated,
                        onRescanFolders = { refreshMedia() }
                    )
                }
            }
        }
    }
    if (showViewSortDialog) {
        ViewAndSortDialog(
            currentViewMode = viewMode,
            currentLayoutMode = layoutMode,
            currentSortOption = sortOption,
            currentSortDirection = sortDirection,
            currentFields = cardFields,
            accentColor = accentColor,
            onDismiss = { showViewSortDialog = false },
            onApply = { vMode, lMode, sOpt, sDir, flds ->
                viewMode = vMode
                layoutMode = lMode
                sortOption = sOpt
                sortDirection = sDir
                cardFields = flds
                onSettingsUpdated(
                    settings.copy(
                        isGridView = lMode == BrowserLayoutMode.GRID,
                        showVideoSize = flds.showSize,
                        showVideoFps = flds.showFps,
                        showNewBadge = flds.showNewTag
                    )
                )
            }
        )
    }

    videoToRename?.let { vid ->
        RenameItemDialog(
            title = "Rename Video",
            initialName = File(vid.path).nameWithoutExtension,
            accentColor = accentColor,
            onDismiss = { videoToRename = null },
            onConfirm = { newName ->
                scope.launch {
                    if (repository.renameVideo(vid, newName)) {
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    videoToCopy?.let { vid ->
        TargetFolderPickerDialog(
            title = "Copy Video to...",
            folders = availableTargetDirs,
            accentColor = accentColor,
            onDismiss = { videoToCopy = null },
            onSelectFolder = { targetDir ->
                scope.launch {
                    Toast.makeText(context, "Copying...", Toast.LENGTH_SHORT).show()
                    if (repository.copyVideoToFolder(vid, targetDir)) {
                        Toast.makeText(context, "Copied to ${targetDir.name}", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    } else {
                        Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    videoToMove?.let { vid ->
        TargetFolderPickerDialog(
            title = "Move Video to...",
            folders = availableTargetDirs,
            accentColor = accentColor,
            onDismiss = { videoToMove = null },
            onSelectFolder = { targetDir ->
                scope.launch {
                    if (repository.moveVideoToFolder(vid, targetDir)) {
                        Toast.makeText(context, "Moved to ${targetDir.name}", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    } else {
                        Toast.makeText(context, "Move failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    videoToDelete?.let { vid ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            containerColor = DarkCard,
            title = { Text("Delete Video?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${vid.displayName}'?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    val target = vid
                    videoToDelete = null
                    scope.launch {
                        if (repository.deleteVideo(target)) {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            refreshMedia()
                        }
                    }
                }) { Text("Delete", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }
    videoProperties?.let { vid ->
        PropertiesInfoDialog(
            title = "Video Properties",
            details = listOf(
                "File Name" to vid.displayName,
                "Folder Path" to vid.folderPath,
                "Size" to FormatUtils.formatSize(vid.sizeBytes),
                "Duration" to FormatUtils.formatDuration(vid.durationMs),
                "Resolution" to "${vid.width} x ${vid.height} (${vid.resolutionLabel})",
                "Frame Rate" to "${vid.fps} FPS",
                "Format" to vid.mimeType
            ),
            accentColor = accentColor,
            onDismiss = { videoProperties = null }
        )
    }

    folderToRename?.let { fld ->
        RenameItemDialog(
            title = "Rename Folder",
            initialName = fld.name,
            accentColor = accentColor,
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                scope.launch {
                    if (repository.renameFolder(fld, newName)) {
                        Toast.makeText(context, "Folder renamed", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    } else {
                        Toast.makeText(context, "Folder rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    folderToCopy?.let { fld ->
        TargetFolderPickerDialog(
            title = "Copy Folder '${fld.name}' to...",
            folders = availableTargetDirs,
            accentColor = accentColor,
            onDismiss = { folderToCopy = null },
            onSelectFolder = { targetDir ->
                scope.launch {
                    Toast.makeText(context, "Copying folder...", Toast.LENGTH_SHORT).show()
                    if (repository.copyFolderTo(fld, targetDir)) {
                        Toast.makeText(context, "Folder copied", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    }
                }
            }
        )
    }

    folderToMove?.let { fld ->
        TargetFolderPickerDialog(
            title = "Move Folder '${fld.name}' to...",
            folders = availableTargetDirs,
            accentColor = accentColor,
            onDismiss = { folderToMove = null },
            onSelectFolder = { targetDir ->
                scope.launch {
                    if (repository.moveFolderTo(fld, targetDir)) {
                        Toast.makeText(context, "Folder moved", Toast.LENGTH_SHORT).show()
                        refreshMedia()
                    }
                }
            }
        )
    }

    folderToDelete?.let { fld ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            containerColor = DarkCard,
            title = { Text("Delete Folder?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Delete all ${fld.videoCount} videos inside '${fld.name}' permanently?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    val target = fld
                    folderToDelete = null
                    scope.launch {
                        if (repository.deleteFolder(target)) {
                            Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                            refreshMedia()
                        }
                    }
                }) { Text("Delete All", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    folderProperties?.let { fld ->
        PropertiesInfoDialog(
            title = "Folder Properties",
            details = listOf(
                "Folder Name" to fld.name,
                "Path" to fld.path,
                "Total Videos" to "${fld.videoCount} videos",
                "Total Size" to FormatUtils.formatSize(fld.totalSizeBytes),
                "Total Duration" to FormatUtils.formatDuration(fld.totalDurationMs)
            ),
            accentColor = accentColor,
            onDismiss = { folderProperties = null }
        )
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun shareVideoFile(context: android.content.Context, video: VideoItem) {
    try {
        val file = File(video.path)
        val shareUri = if (file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            video.uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
    }
}
