package com.zyfen.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.zyfen.music.playback.PlayerManager
import com.zyfen.music.ui.components.ConnectedMiniPlayer
import com.zyfen.music.ui.nav.Route
import com.zyfen.music.ui.screens.*
import com.zyfen.music.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class BottomNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {

    private val player: PlayerManager by lazy { ZyfenApp.container.playerManager }

    private var audioGranted by mutableStateOf(false)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        audioGranted = hasAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        audioGranted = hasAudioPermission()
        requestAudioPerms()
        setContent {
            val settingsStore = ZyfenApp.container.settingsStore
            val currentThemeMode by settingsStore.themeMode.collectAsState(initial = "spotify")
            val currentAccentHex by settingsStore.accentColor.collectAsState(initial = "#1DB954")
            val currentUiStyle by settingsStore.uiStyle.collectAsState(initial = "glass")
            var showSplash by remember { mutableStateOf(true) }

            ZyfenTheme(
                themeMode = currentThemeMode,
                accentColorHex = currentAccentHex,
                uiStyle = currentUiStyle
            ) {
                val isGlass = currentUiStyle == "glass"
                val activeAccent = parseHexColor(currentAccentHex)

                @Composable
                fun AppRootContainer(content: @Composable BoxScope.() -> Unit) {
                    if (isGlass) {
                        LiquidMeshBackground(accentColor = activeAccent, content = content)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            content = content
                        )
                    }
                }

                AppRootContainer {
                    if (showSplash) {
                        com.zyfen.music.ui.components.ZyfenSplashScreen(
                            onFinished = { showSplash = false }
                        )
                    } else {
                        val nav = rememberNavController()
                        val lib: LibraryViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    val c = ZyfenApp.container
                                    return LibraryViewModel(c.localRepo, c.songDao, c.playlistDao, c.playerManager, c.songDownloader) as T
                                }
                            }
                        )

                        LaunchedEffect(Unit) {
                            player.events.collect {
                                android.widget.Toast.makeText(
                                    this@MainActivity, it, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        val back by nav.currentBackStackEntryAsState()
                        val route = back?.destination?.route ?: Route.Home.path
                        val chromeVisible = route != Route.NowPlaying.path && route != Route.Queue.path

                        val bottomTabs = remember {
                            listOf(
                                BottomNavTab("Discover", Icons.Filled.Home, Icons.Outlined.Home, Route.Home.path),
                                BottomNavTab("Music", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, Route.Library.path),
                                BottomNavTab("Search", Icons.Filled.Search, Icons.Outlined.Search, Route.Search.path),
                                BottomNavTab("Account", Icons.Filled.Person, Icons.Outlined.Person, Route.Settings.path)
                            )
                        }

                        val selectedIndex = when {
                            route.startsWith("playlist/") -> 1
                            route.startsWith("album/") -> 1
                            route.startsWith("artist/") -> 1
                            route == Route.SpotifyImport.path -> 1
                            route == Route.Library.path || route == Route.Songs.path -> 1
                            route == Route.Search.path -> 2
                            route == Route.Settings.path -> 3
                            else -> 0
                        }

                        val openSearch: () -> Unit = {
                            nav.navigate(Route.Search.path) {
                                popUpTo(Route.Home.path) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentWindowInsets = if (chromeVisible) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (chromeVisible) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 2.dp)
                                    ) {
                                        ConnectedMiniPlayer(player) {
                                            nav.navigate(Route.NowPlaying.path)
                                        }

                                        // Navigation Pill Bar (Glass / Classic Solid)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .navigationBarsPadding(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val pillShape = RoundedCornerShape(32.dp)
                                            val pillBorder = if (isGlass) {
                                                BorderStroke(
                                                    1.dp,
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.40f),
                                                            Color.White.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                )
                                            } else {
                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(62.dp)
                                                    .shadow(
                                                        elevation = 16.dp,
                                                        shape = pillShape,
                                                        spotColor = Color.Black.copy(alpha = 0.55f),
                                                        ambientColor = Color.White.copy(alpha = 0.10f)
                                                    ),
                                                shape = pillShape,
                                                color = Color.Transparent,
                                                border = pillBorder
                                            ) {
                                                val bgBrush = if (isGlass) {
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.16f),
                                                            Color(0xFF140D26).copy(alpha = 0.90f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color(0xFF1E1E1E),
                                                            Color(0xFF121212)
                                                        )
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(bgBrush)
                                                        .padding(horizontal = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceAround,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        bottomTabs.forEachIndexed { i, tab ->
                                                            val selected = selectedIndex == i
                                                            val tabAccent = LocalAccentColor.current
                                                            val tabBg = if (selected) {
                                                                if (isGlass) tabAccent.copy(alpha = 0.28f)
                                                                else tabAccent.copy(alpha = 0.22f)
                                                            } else Color.Transparent

                                                            val tabBorder = if (selected) {
                                                                BorderStroke(1.dp, tabAccent.copy(alpha = 0.70f))
                                                            } else null

                                                            Surface(
                                                                onClick = {
                                                                    nav.navigate(tab.route) {
                                                                        popUpTo(Route.Home.path) { saveState = true }
                                                                        launchSingleTop = true
                                                                        restoreState = true
                                                                    }
                                                                },
                                                                shape = RoundedCornerShape(22.dp),
                                                                color = tabBg,
                                                                border = tabBorder,
                                                                modifier = Modifier
                                                                    .height(44.dp)
                                                                    .weight(if (selected) 1.25f else 0.9f)
                                                                    .padding(horizontal = 3.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    horizontalArrangement = Arrangement.Center,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                                        contentDescription = tab.title,
                                                                        tint = if (selected) tabAccent else Color.White.copy(alpha = 0.65f),
                                                                        modifier = Modifier.size(22.dp)
                                                                    )
                                                                    if (selected) {
                                                                        Spacer(Modifier.width(6.dp))
                                                                        Text(
                                                                            text = tab.title,
                                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                                fontWeight = FontWeight.Bold,
                                                                                shadow = if (isGlass) LiquidGlassTokens.SubtleTextShadow else null
                                                                            ),
                                                                            color = Color.White,
                                                                            maxLines = 1
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                NavHost(
                                    nav,
                                    startDestination = Route.Home.path,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                            composable(Route.Home.path) {
                                HomeScreen(
                                    lib,
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) },
                                    onOpenPlaylist = { nav.navigate(Route.PlaylistDetail.id(it)) },
                                    onOpenSpotifyImport = { nav.navigate(Route.SpotifyImport.path) },
                                    onOpenSearch = openSearch,
                                    onOpenFavorites = { nav.navigate(Route.Favorites.path) },
                                    onOpenOffline = { nav.navigate(Route.Offline.path) },
                                    onOpenHistory = { nav.navigate(Route.History.path) },
                                    hasAudioPermission = { audioGranted },
                                    onRequestAudio = { requestAudioPerms() }
                                )
                            }
                            composable(Route.Favorites.path) {
                                FavoritesScreen(
                                    lib,
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) },
                                    onBack = { nav.popBackStack() }
                                )
                            }
                            composable(Route.History.path) {
                                HistoryScreen(
                                    lib,
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) },
                                    onBack = { nav.popBackStack() }
                                )
                            }
                            composable(Route.Offline.path) {
                                OfflineScreen(
                                    lib,
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) },
                                    onBack = { nav.popBackStack() }
                                )
                            }
                            composable(Route.Songs.path) {
                                SongsScreen(lib, onOpenPlayer = { nav.navigate(Route.NowPlaying.path) }, onOpenSearch = openSearch)
                            }
                            composable(Route.Search.path) {
                                FullSearchScreen(lib, onOpenPlayer = { nav.navigate(Route.NowPlaying.path) })
                            }
                            composable(Route.Library.path) {
                                LibraryScreen(
                                    lib,
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) },
                                    onOpenPlaylist = { nav.navigate(Route.PlaylistDetail.id(it)) },
                                    onOpenSpotifyImport = { nav.navigate(Route.SpotifyImport.path) }
                                )
                            }
                            composable(Route.Artists.path) {
                                ArtistsScreen(lib, onOpenArtist = { nav.navigate(Route.ArtistDetail.name(it)) }, onOpenSearch = openSearch)
                            }
                            composable(Route.Albums.path) {
                                AlbumsScreen(lib, onOpenAlbum = { nav.navigate(Route.AlbumDetail.name(it)) }, onOpenSearch = openSearch)
                            }
                            composable(Route.SpotifyImport.path) {
                                SpotifyImportScreen(lib = lib, onBack = { nav.popBackStack() })
                            }
                            composable(Route.Settings.path) {
                                FullSettingsScreen(onBack = { nav.popBackStack() }, vm = lib)
                            }
                            composable(
                                route = Route.PlaylistDetail.path,
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { entry ->
                                val pid = entry.arguments?.getString("id") ?: return@composable
                                PlaylistDetailScreen(
                                    lib, pid,
                                    onBack = { nav.popBackStack() },
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) }
                                )
                            }
                            composable(
                                route = Route.AlbumDetail.path,
                                arguments = listOf(navArgument("name") { type = NavType.StringType })
                            ) { entry ->
                                val name = entry.arguments?.getString("name") ?: return@composable
                                AlbumDetailScreen(
                                    lib, name,
                                    onBack = { nav.popBackStack() },
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) }
                                )
                            }
                            composable(
                                route = Route.ArtistDetail.path,
                                arguments = listOf(navArgument("name") { type = NavType.StringType })
                            ) { entry ->
                                val name = entry.arguments?.getString("name") ?: return@composable
                                ArtistDetailScreen(
                                    lib, name,
                                    onBack = { nav.popBackStack() },
                                    onOpenPlayer = { nav.navigate(Route.NowPlaying.path) }
                                )
                            }
                            composable(Route.NowPlaying.path) {
                                NowPlayingScreen(
                                    player, lib,
                                    onOpenQueue = { nav.navigate(Route.Queue.path) },
                                    onBack = { nav.popBackStack() }
                                )
                            }
                            composable(Route.Queue.path) {
                                QueueScreen(player, lib, onBack = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}
}

    override fun onResume() {
        super.onResume()
        audioGranted = hasAudioPermission()
    }

    private fun requestAudioPerms() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (check(Manifest.permission.READ_MEDIA_AUDIO)) perms += Manifest.permission.READ_MEDIA_AUDIO
            if (check(Manifest.permission.POST_NOTIFICATIONS)) perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            if (check(Manifest.permission.READ_EXTERNAL_STORAGE)) perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
        else audioGranted = hasAudioPermission()
    }

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun check(p: String) =
        ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED
}
