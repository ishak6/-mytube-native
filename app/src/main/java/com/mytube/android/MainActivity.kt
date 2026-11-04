package com.mytube.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

private data class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private data class LocalVideo(val uri: String, val title: String)

private val appTabs = listOf(
    AppTab("Home", Icons.Filled.Home),
    AppTab("Search", Icons.Filled.Search),
    AppTab("Shorts", Icons.Filled.SmartDisplay),
    AppTab("Library", Icons.Filled.VideoLibrary),
    AppTab("Premium", Icons.Filled.Star)
)

class MainActivity : ComponentActivity() {
    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some document providers do not offer persistable permissions.
            }
            val title = queryDisplayName(uri) ?: "Video ${System.currentTimeMillis()}"
            setContent { MyTubeApp(onImport = { launchVideoPicker() }, initialImport = LocalVideo(uri.toString(), title)) }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun launchVideoPicker() {
        videoPicker.launch(arrayOf("video/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyTubeApp(onImport = { launchVideoPicker() }) }
    }
}

@Composable
private fun MyTubeApp(
    onImport: (() -> Unit)? = null,
    initialImport: LocalVideo? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videos = remember { mutableStateListOf<LocalVideo>().apply { addAll(loadVideos(context)) } }
    var selectedTab by remember { mutableStateOf(0) }
    var darkMode by remember { mutableStateOf(true) }
    val privacyPrefs = remember { context.getSharedPreferences("mytube_privacy", Context.MODE_PRIVATE) }
    var showMyTubePromotions by remember { mutableStateOf(privacyPrefs.getBoolean("show_myTube_promotions", false)) }
    var usageAnalytics by remember { mutableStateOf(privacyPrefs.getBoolean("usage_analytics", false)) }
    var braveProtection by remember { mutableStateOf(privacyPrefs.getBoolean("brave_protection", true)) }
    var braveStatus by remember { mutableStateOf("Initializing Brave filter engine…") }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val youtubePrefs = remember { context.getSharedPreferences("mytube_youtube", Context.MODE_PRIVATE) }
    var youtubeApiKey by remember { mutableStateOf(youtubePrefs.getString("api_key", "") ?: "") }
    var activeVideo by remember { mutableStateOf<LocalVideo?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        NativeAdblock.setEnabled(braveProtection)
        NativeAdblock.initialize { status -> braveStatus = status }
    }

    androidx.compose.runtime.LaunchedEffect(initialImport?.uri) {
        val incoming = initialImport
        if (incoming != null && videos.none { it.uri == incoming.uri }) {
            videos.add(0, incoming)
            saveVideos(context, videos)
            player.setMediaItem(MediaItem.fromUri(Uri.parse(incoming.uri)))
            player.prepare()
            player.playWhenReady = true
            activeVideo = incoming
            selectedTab = 0
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    BackHandler(enabled = showSettings || activeVideo != null || selectedTab != 0) {
        when {
            showSettings -> showSettings = false
            activeVideo != null -> {
                player.pause()
                activeVideo = null
            }
            else -> selectedTab = 0
        }
    }

    val colors = if (darkMode) darkColorScheme(
        primary = Color(0xFFFF4968),
        secondary = Color(0xFFFF8A9D),
        background = Color(0xFF101114),
        surface = Color(0xFF1B1D22)
    ) else lightColorScheme(
        primary = Color(0xFFE32645),
        secondary = Color(0xFFB32642),
        background = Color(0xFFF7F7F8),
        surface = Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    appTabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index && !showSettings,
                            onClick = {
                                showSettings = false
                                selectedTab = index
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("mytube", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Your video space", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    IconButton(onClick = { showSettings = !showSettings; activeVideo = null; player.pause() }) {
                        Icon(if (showSettings) Icons.Filled.ArrowBack else Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
                Spacer(Modifier.height(12.dp))

                when {
                    showSettings -> SettingsScreen(
                        darkMode = darkMode,
                        onDarkModeChange = { darkMode = it },
                        showMyTubePromotions = showMyTubePromotions,
                        onPromotionsChange = {
                            showMyTubePromotions = it
                            privacyPrefs.edit().putBoolean("show_myTube_promotions", it).apply()
                        },
                        usageAnalytics = usageAnalytics,
                        onAnalyticsChange = {
                            usageAnalytics = it
                            privacyPrefs.edit().putBoolean("usage_analytics", it).apply()
                        },
                        braveProtection = braveProtection,
                        onBraveProtectionChange = { enabled ->
                            braveProtection = enabled
                            NativeAdblock.setEnabled(enabled)
                            privacyPrefs.edit().putBoolean("brave_protection", enabled).apply()
                        },
                        braveStatus = braveStatus,
                        onRefreshBrave = { NativeAdblock.refresh { status -> braveStatus = status } },
                        youtubeApiKey = youtubeApiKey,
                        onYouTubeApiKeyChange = { key ->
                            youtubeApiKey = key
                            youtubePrefs.edit().putString("api_key", key.trim()).apply()
                        }
                    )
                    activeVideo != null -> PlayerScreen(video = activeVideo!!, player = player, onClose = {
                        player.pause()
                        activeVideo = null
                    })
                    selectedTab == 0 -> HomeScreen(
                        videos = videos,
                        youtubeApiKey = youtubeApiKey,
                        onImport = { onImport?.invoke() },
                        onPlay = { video -> playVideo(video, player) { activeVideo = video } },
                        onLibrary = { selectedTab = 3 }
                    )
                    selectedTab == 1 -> SearchScreen(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        videos = videos,
                        youtubeApiKey = youtubeApiKey,
                        onPlay = { playVideo(it, player) { activeVideo = it } },
                        onImport = { onImport?.invoke() }
                    )
                    selectedTab == 2 -> ShortsScreen(videos = videos, youtubeApiKey = youtubeApiKey, onPlay = { playVideo(it, player) { activeVideo = it } }, onImport = { onImport?.invoke() })
                    selectedTab == 3 -> LibraryScreen(
                        videos = videos,
                        onPlay = { playVideo(it, player) { activeVideo = it } },
                        onImport = { onImport?.invoke() },
                        onDelete = {
                            videos.remove(it)
                            saveVideos(context, videos)
                        }
                    )
                    else -> PremiumScreen(onSettings = { showSettings = true })
                }
            }
        }
    }
}

private fun playVideo(video: LocalVideo, player: ExoPlayer, onPlaying: () -> Unit) {
    player.setMediaItem(MediaItem.fromUri(Uri.parse(video.uri)))
    player.prepare()
    player.playWhenReady = true
    onPlaying()
}

@Composable
private fun HomeScreen(
    videos: List<LocalVideo>,
    youtubeApiKey: String,
    onImport: () -> Unit,
    onPlay: (LocalVideo) -> Unit,
    onLibrary: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Welcome to mytube", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("A native video space for your own and authorized media.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImport) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Import video")
                        }
                        FilledTonalButton(onClick = onLibrary) { Text("Open library") }
                    }
                    Text("NATIVE ANDROID • NO WEBVIEW", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { YouTubeHomeFeed(apiKey = youtubeApiKey) }
        item { Text("Recently added", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        if (videos.isEmpty()) {
            item { EmptyCard("Your library is empty", "Tap Import video to choose a video stored on your phone.") }
        } else {
            items(videos.take(8), key = { it.uri }) { video -> VideoRow(video = video, onPlay = { onPlay(video) }) }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    videos: List<LocalVideo>,
    youtubeApiKey: String,
    onPlay: (LocalVideo) -> Unit,
    onImport: () -> Unit
) {
    val results = videos.filter { it.title.contains(query, ignoreCase = true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        YouTubeSearchFeed(apiKey = youtubeApiKey)
        Text("Search your videos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search imported videos") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        if (results.isEmpty()) {
            EmptyCard("No matching videos", if (videos.isEmpty()) "Import a video first. Search currently covers your local library." else "Try another title.")
            FilledTonalButton(onClick = onImport) { Icon(Icons.Filled.Add, null); Text("  Import video") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.uri }) { video -> VideoRow(video = video, onPlay = { onPlay(video) }) }
            }
        }
    }
}

@Composable
private fun ShortsScreen(videos: List<LocalVideo>, youtubeApiKey: String, onPlay: (LocalVideo) -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        YouTubeShortsFeed(apiKey = youtubeApiKey)
        Text("Shorts • Local videos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        EmptyCard("Local short-video shelf", "This section can play videos you import. A public Shorts feed is not connected because it requires an authorized content source.")
        if (videos.isEmpty()) {
            Button(onClick = onImport) { Icon(Icons.Filled.Add, null); Text("  Import a video") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = { it.uri }) { video -> VideoRow(video = video, onPlay = { onPlay(video) }) }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    videos: List<LocalVideo>,
    onPlay: (LocalVideo) -> Unit,
    onImport: () -> Unit,
    onDelete: (LocalVideo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your Library", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = onImport) { Icon(Icons.Filled.Add, null); Text(" Add") }
        }
        if (videos.isEmpty()) {
            EmptyCard("No videos saved", "Import videos from your phone. They will remain listed in this library.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = { it.uri }) { video ->
                    VideoRow(video = video, onPlay = { onPlay(video) }, onDelete = { onDelete(video) })
                }
            }
        }
    }
}

@Composable
private fun PremiumScreen(onSettings: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("mytube Premium", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        item { EmptyCard("Your app, your features", "The mytube interface has no in-app advertising in this build. No paid unlock is being simulated.") }
        item { FeatureCard("Native playback", "Media3 player for local videos.") }
        item { FeatureCard("Private local library", "Imported video references are stored on this device.") }
        item { FeatureCard("No WebView", "The app interface is built with Jetpack Compose.") }
        item { EmptyCard("External services", "YouTube and other providers may have their own ads, subscriptions, API terms, and playback restrictions. This app does not bypass them.") }
        item { Button(onClick = onSettings) { Icon(Icons.Filled.Settings, null); Text("  App settings") } }
    }
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    showMyTubePromotions: Boolean,
    onPromotionsChange: (Boolean) -> Unit,
    usageAnalytics: Boolean,
    onAnalyticsChange: (Boolean) -> Unit,
    braveProtection: Boolean,
    onBraveProtectionChange: (Boolean) -> Unit,
    braveStatus: String,
    onRefreshBrave: () -> Unit,
    youtubeApiKey: String,
    onYouTubeApiKeyChange: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Settings", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        item {
            SettingSwitchCard(
                title = "Dark theme",
                description = "Use the dark appearance throughout mytube.",
                checked = darkMode,
                onCheckedChange = onDarkModeChange
            )
        }
        item { Text("YouTube connection", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YouTube Data API v3", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = youtubeApiKey, onValueChange = onYouTubeApiKeyChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("API key") })
                    Text("Google Cloud Console-এ YouTube Data API v3 enable করে API key তৈরি করুন। এই key দিয়ে public video search ও metadata feed চলবে; এটি video stream URL দেয় না।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        item { Text("Privacy & ad protection", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item {
            SettingSwitchCard(
                title = "Brave network protection",
                description = "Use Brave adblock-rust to check requests made through mytube’s own protected HTTP client. This does not intercept YouTube player traffic.",
                checked = braveProtection,
                onCheckedChange = onBraveProtectionChange
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Brave filter engine", fontWeight = FontWeight.SemiBold)
                    Text(braveStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Filter rules are downloaded from EasyList and EasyPrivacy. The engine only affects requests routed through mytube’s NativeAdblock HTTP client.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    TextButton(onClick = onRefreshBrave) { Text("Update filter lists") }
                }
            }
        }
        item {
            SettingSwitchCard(
                title = "mytube promotions",
                description = "Allow promotional placements owned by mytube. Off by default; this build has no in-app ad placements or ad SDK.",
                checked = showMyTubePromotions,
                onCheckedChange = onPromotionsChange
            )
        }
        item {
            SettingSwitchCard(
                title = "Usage analytics",
                description = "Allow optional app usage analytics. Off by default; no analytics SDK is currently included.",
                checked = usageAnalytics,
                onCheckedChange = onAnalyticsChange
            )
        }
        item {
            FeatureCard(
                "Tracker protection",
                "Brave adblock-rust is available to mytube-owned HTTP requests routed through NativeAdblock. No current online feed/player request is connected to that client yet."
            )
        }
        item {
            FeatureCard(
                "YouTube ad status",
                "Brave’s filter engine cannot automatically inspect or remove ads inside YouTube’s player pipeline. This app currently has no YouTube stream integration."
            )
        }
        item { EmptyCard("Local privacy", "Your imported media list is stored on this device. Video files are not uploaded by mytube.") }
        item { EmptyCard("App status", "Native Compose interface and local Media3 playback are implemented. Online discovery, account sync, and cloud downloads are not connected.") }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlayerScreen(video: LocalVideo, player: ExoPlayer, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
        Text("Playing from your device", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Playback availability depends on the selected file's format and Android device codecs.")
    }
}

@Composable
private fun VideoRow(video: LocalVideo, onPlay: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("Local video • Tap to play", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove from library") }
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun loadVideos(context: Context): List<LocalVideo> {
    val raw = context.getSharedPreferences("mytube_library", Context.MODE_PRIVATE).getString("videos", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(LocalVideo(item.getString("uri"), item.getString("title")))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveVideos(context: Context, videos: List<LocalVideo>) {
    val array = JSONArray()
    videos.forEach { video ->
        array.put(JSONObject().put("uri", video.uri).put("title", video.title))
    }
    context.getSharedPreferences("mytube_library", Context.MODE_PRIVATE)
        .edit().putString("videos", array.toString()).apply()
}
