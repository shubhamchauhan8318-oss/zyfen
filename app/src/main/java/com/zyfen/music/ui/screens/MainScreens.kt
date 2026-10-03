package com.zyfen.music.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.zyfen.music.data.local.HistoryEntity
import com.zyfen.music.data.local.PlaylistEntity
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.media.toSong
import com.zyfen.music.ui.components.*
import com.zyfen.music.ui.theme.*

// -------------------------------------------------------------
// 1. HOME SCREEN
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSpotifyImport: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenOffline: () -> Unit = {},
    hasAudioPermission: () -> Boolean = { true },
    onRequestAudio: () -> Unit = {}
) {
    val songs by vm.songs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val history by vm.history.collectAsState()
    val local = remember(songs) { songs.filter { it.isLocal } }
    val hasPerm = hasAudioPermission()
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(local.isEmpty(), hasPerm) {
        if (local.isEmpty() && hasPerm) {
            vm.refresh()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_zyfen_logo),
                            contentDescription = "ZYFEN Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                "ZYFEN MUSIC",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Your personal music player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // Liquid Frosted Search Bar
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.40f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable(onClick = onOpenSearch),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = LocalAccentColor.current,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "What do you want to play? Search any song…",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                shadow = LiquidGlassTokens.SubtleTextShadow
                            ),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Quick Category Shortcuts
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPillButton(
                        text = "Favorites (${favorites.size})",
                        icon = Icons.Filled.Favorite,
                        onClick = onOpenFavorites
                    )
                    GlassPillButton(
                        text = "Offline (${local.size})",
                        icon = Icons.Filled.Folder,
                        onClick = onOpenOffline
                    )
                    GlassPillButton(
                        text = "History",
                        icon = Icons.Filled.History,
                        onClick = onOpenHistory
                    )
                }
            }

            // Audio Permission Card if needed
            if (!hasPerm) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        fillAlpha = 0.10f
                    ) {
                        Text(
                            "Local Music Access",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = LiquidGlassTokens.TextShadow
                            ),
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Grant storage access to scan and play all audio files on your device (MP3, M4A, FLAC, WAV, OGG).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                shadow = LiquidGlassTokens.SubtleTextShadow
                            ),
                            color = Color(0xFFD8B4FE)
                        )
                        Spacer(Modifier.height(12.dp))
                        GlassPillButton(
                            text = "Allow Access",
                            selected = true,
                            onClick = onRequestAudio
                        )
                    }
                }
            }

            // Recently Played Carousel
            if (history.isNotEmpty()) {
                item {
                    SectionHeader("Recently Played", onSeeAll = onOpenHistory)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(history.take(10)) { h ->
                            HistoryCard(h, onClick = {
                                val s = Song(
                                    id = h.songId,
                                    title = h.title,
                                    artist = h.artist,
                                    album = h.album,
                                    durationMs = h.durationMs,
                                    contentUri = h.uri,
                                    artworkUri = h.artworkUri,
                                    isLocal = h.isLocal
                                )
                                vm.player.playSongs(listOf(s), 0)
                                onOpenPlayer()
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Playlists Section
            item {
                SectionHeader("Playlists", onAction = onOpenSpotifyImport, actionLabel = "Import Spotify")
                if (playlists.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSpotifyImport,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudDownload, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import Spotify Playlist or Create New")
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists) { p ->
                            PlaylistCard(p, onClick = { onOpenPlaylist(p.id) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Quick Picks (Songs)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quick Picks",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZyfenText
                    )
                    if (songs.isNotEmpty()) {
                        Row {
                            TextButton(onClick = { vm.playAll(0); onOpenPlayer() }) {
                                Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play all", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (hasPerm) "No songs found on device. Tap search or import Spotify playlists."
                            else "Grant media permission to view local songs.",
                            color = ZyfenTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                itemsIndexed(songs.take(25), key = { _, s -> s.id }) { index, s ->
                    SongRow(
                        song = s,
                        onClick = {
                            vm.player.playSongs(songs, index)
                            onOpenPlayer()
                        },
                        onFav = { vm.toggleFavorite(s) },
                        onMore = { songForPlaylist = s }
                    )
                }
            }
        }

        if (songForPlaylist != null) {
            AddToPlaylistSheet(
                song = songForPlaylist,
                playlists = playlists,
                onCreatePlaylist = { vm.createPlaylist(it) },
                onAddToPlaylist = { pid, song -> vm.addToPlaylist(pid, song) },
                onDismiss = { songForPlaylist = null }
            )
        }
    }
}

// -------------------------------------------------------------
// 2. SEARCH SCREEN
// -------------------------------------------------------------
@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit
) {
    val q by vm.query.collectAsState()
    val list by vm.filtered.collectAsState()
    var filterLocalOnly by remember { mutableStateOf(false) }

    val displayed = remember(list, filterLocalOnly) {
        if (filterLocalOnly) list.filter { it.isLocal } else list
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        ScreenHeader("Search Music")

        OutlinedTextField(
            value = q,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Clear, "Clear", tint = ZyfenTextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = ZyfenCard,
                focusedContainerColor = ZyfenCard,
                unfocusedContainerColor = ZyfenCard
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !filterLocalOnly,
                onClick = { filterLocalOnly = false },
                label = { Text("All Tracks") }
            )
            FilterChip(
                selected = filterLocalOnly,
                onClick = { filterLocalOnly = true },
                label = { Text("Device Audio Only") }
            )
        }

        if (displayed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (q.isBlank()) "Type title, artist, or album name to search"
                    else "No matching tracks found for '$q'",
                    color = ZyfenTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(displayed, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = {
                            vm.player.playSongs(displayed, i)
                            onOpenPlayer()
                        },
                        onFav = { vm.toggleFavorite(s) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. LIBRARY SCREEN
// -------------------------------------------------------------
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSpotifyImport: () -> Unit
) {
    val songs by vm.songs.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val history by vm.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Playlists", "Favorites", "History")
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

    Column(
        Modifier.fillMaxSize()
    ) {
        ScreenHeader(
            "My Library",
            actions = {
                IconButton(onClick = onOpenSpotifyImport) {
                    Icon(Icons.Filled.CloudDownload, "Import", tint = ZyfenText)
                }
            }
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = {},
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Tab(
                    selected = selected,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selected) MaterialTheme.colorScheme.primary else ZyfenTextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> { // All Songs
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${songs.size} tracks", color = ZyfenTextSecondary, style = MaterialTheme.typography.bodySmall)
                            PillButton("Play All", onClick = { vm.playAll(0); onOpenPlayer() })
                        }
                    }
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            song = s,
                            onClick = { vm.player.playSongs(songs, i); onOpenPlayer() },
                            onFav = { vm.toggleFavorite(s) },
                            onMore = { songForPlaylist = s }
                        )
                    }
                }
            }
            1 -> { // Playlists
                PlaylistsScreen(vm, onOpenPlaylist, onOpenSpotifyImport)
            }
            2 -> { // Favorites
                FavoritesScreen(vm, onOpenPlayer)
            }
            3 -> { // History
                HistoryScreen(vm, onOpenPlayer)
            }
        }

        if (songForPlaylist != null) {
            AddToPlaylistSheet(
                song = songForPlaylist,
                playlists = playlists,
                onCreatePlaylist = { vm.createPlaylist(it) },
                onAddToPlaylist = { pid, song -> vm.addToPlaylist(pid, song) },
                onDismiss = { songForPlaylist = null }
            )
        }
    }
}

// -------------------------------------------------------------
// 4. PLAYLISTS SCREEN
// -------------------------------------------------------------
@Composable
fun PlaylistsScreen(
    vm: LibraryViewModel,
    onOpenPlaylist: (String) -> Unit,
    onOpenSpotifyImport: () -> Unit
) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var coverPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${playlists.size} playlists", color = ZyfenTextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Playlist")
            }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No playlists yet", color = ZyfenTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showCreate = true }) {
                        Text("Create Playlist")
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = {
                            Text(p.name, color = ZyfenText, style = MaterialTheme.typography.titleSmall)
                        },
                        supportingContent = {
                            Text(if (p.isSpotifyImport) "Spotify Import" else "Custom Playlist", color = ZyfenTextSecondary)
                        },
                        leadingContent = {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Artwork(p.artworkUrl, Modifier.size(54.dp), 10)
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { coverPlaylist = p }) {
                                    Icon(Icons.Filled.PhotoCamera, "Change Photo", tint = ZyfenTextSecondary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { vm.deletePlaylist(p.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, "Delete", tint = ZyfenTextSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .clickable { onOpenPlaylist(p.id) }
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }

    coverPlaylist?.let { pl ->
        ChangePlaylistCoverDialog(
            playlistId = pl.id,
            currentArtworkUrl = pl.artworkUrl,
            songArtworks = emptyList(),
            onDismiss = { coverPlaylist = null },
            onSaveCover = { newCover ->
                vm.updatePlaylistArtwork(pl.id, newCover)
                coverPlaylist = null
            }
        )
    }

    if (showCreate) {
        val accent = LocalAccentColor.current
        GlassDialog(
            onDismissRequest = { showCreate = false },
            title = "Create New Playlist",
            confirmButton = {
                GlassPillButton(
                    text = "Create",
                    selected = true,
                    onClick = {
                        if (name.isNotBlank()) {
                            vm.createPlaylist(name.trim())
                            name = ""
                            showCreate = false
                        }
                    }
                )
            },
            dismissButton = {
                GlassPillButton(text = "Cancel", onClick = { showCreate = false })
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -------------------------------------------------------------
// 5. FAVORITES SCREEN
// -------------------------------------------------------------
@Composable
fun FavoritesScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        androidx.activity.compose.BackHandler { onBack() }
    }
    val favorites by vm.favorites.collectAsState()

    Column(
        Modifier.fillMaxSize()
    ) {
        ScreenHeader(
            "Favorites",
            leading = onBack?.let { backAction ->
                {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        )

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FavoriteBorder, null, tint = ZyfenTextDisabled, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No favorite songs yet", color = ZyfenTextSecondary)
                    Text("Tap the heart icon on any song to add it here", color = ZyfenTextDisabled, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${favorites.size} songs", color = ZyfenTextSecondary, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Play All", onClick = {
                        vm.player.playSongs(favorites, 0)
                        onOpenPlayer()
                    })
                    PillButton("Shuffle", onClick = {
                        vm.player.playSongs(favorites.shuffled(), 0)
                        onOpenPlayer()
                    })
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(favorites, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = {
                            vm.player.playSongs(favorites, i)
                            onOpenPlayer()
                        },
                        onFav = { vm.toggleFavorite(s) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. HISTORY SCREEN
// -------------------------------------------------------------
@Composable
fun HistoryScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        androidx.activity.compose.BackHandler { onBack() }
    }
    val history by vm.history.collectAsState()

    Column(
        Modifier.fillMaxSize()
    ) {
        ScreenHeader(
            "Listening History",
            leading = onBack?.let { backAction ->
                {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = { vm.clearHistory() }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear History", tint = ZyfenTextSecondary)
                    }
                }
            }
        )

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, tint = ZyfenTextDisabled, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No playback history yet", color = ZyfenTextSecondary)
                    Text("Songs you play will show up here", color = ZyfenTextDisabled, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it.historyId }) { h ->
                    val s = Song(
                        id = h.songId,
                        title = h.title,
                        artist = h.artist,
                        album = h.album,
                        durationMs = h.durationMs,
                        contentUri = h.uri,
                        artworkUri = h.artworkUri,
                        isLocal = h.isLocal
                    )
                    ListItem(
                        headlineContent = {
                            Text(h.title, color = ZyfenText, maxLines = 1, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        },
                        supportingContent = {
                            Text("${h.artist} • ${formatTimeAgo(h.playedAt)}", color = ZyfenTextSecondary, maxLines = 1)
                        },
                        leadingContent = {
                            Artwork(
                                url = h.artworkUri,
                                modifier = Modifier.size(48.dp),
                                corner = 12,
                                seed = h.songId,
                                title = h.title
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.deleteHistoryItem(h.historyId) }) {
                                Icon(Icons.Filled.Close, "Remove", tint = ZyfenTextDisabled, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier
                            .clickable {
                                vm.player.playSongs(listOf(s), 0)
                                onOpenPlayer()
                            }
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. OFFLINE / DOWNLOADS MUSIC SCREEN
// -------------------------------------------------------------
@Composable
fun OfflineScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        androidx.activity.compose.BackHandler { onBack() }
    }
    val songs by vm.songs.collectAsState()
    val localSongs = remember(songs) { songs.filter { it.isLocal } }
    var selectedFormat by remember { mutableStateOf("All") }
    val formats = listOf("All", "MP3", "M4A", "FLAC", "WAV", "OGG")

    val filteredByFormat = remember(localSongs, selectedFormat) {
        if (selectedFormat == "All") localSongs
        else localSongs.filter { s ->
            s.filePath?.endsWith(".$selectedFormat", ignoreCase = true) == true ||
            s.contentUri.endsWith(".$selectedFormat", ignoreCase = true)
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        ScreenHeader(
            "Offline & Downloads",
            leading = onBack?.let { backAction ->
                {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, "Rescan", tint = ZyfenText)
                }
            }
        )
        Text(
            "Device files & songs you like are automatically downloaded here for offline listening.",
            color = ZyfenTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(4.dp))

        // Format selector
        ScrollableTabRow(
            selectedTabIndex = formats.indexOf(selectedFormat).coerceAtLeast(0),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = {},
            divider = {}
        ) {
            formats.forEach { fmt ->
                val sel = selectedFormat == fmt
                FilterChip(
                    selected = sel,
                    onClick = { selectedFormat = fmt },
                    label = { Text(fmt) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filteredByFormat.size} offline tracks", color = ZyfenTextSecondary, style = MaterialTheme.typography.bodySmall)
            if (filteredByFormat.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Play All", onClick = {
                        vm.player.playSongs(filteredByFormat, 0)
                        onOpenPlayer()
                    })
                    PillButton("Shuffle", onClick = {
                        vm.player.playSongs(filteredByFormat.shuffled(), 0)
                        onOpenPlayer()
                    })
                }
            }
        }

        if (filteredByFormat.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, null, tint = ZyfenTextDisabled, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No $selectedFormat tracks found offline", color = ZyfenTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan Device Audio")
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filteredByFormat, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = {
                            vm.player.playSongs(filteredByFormat, i)
                            onOpenPlayer()
                        },
                        onFav = { vm.toggleFavorite(s) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PLAYLIST DETAIL SCREEN
// -------------------------------------------------------------
@Composable
fun PlaylistDetailScreen(
    vm: LibraryViewModel,
    playlistId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }
    LaunchedEffect(playlistId) { vm.loadPlaylist(playlistId) }
    val detail by vm.detail.collectAsState()
    val p = detail?.playlist
    val songs = remember(detail) { detail?.songs.orEmpty().map { it.toSong() } }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showChangeCover by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
    ) {
        DetailHeader(
            p?.name ?: "Playlist",
            onBack = onBack
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 48.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Artwork(
                        url = p?.artworkUrl ?: songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showChangeCover = true },
                        corner = 20,
                        seed = p?.id.orEmpty(),
                        title = p?.name.orEmpty()
                    )

                    Surface(
                        onClick = { showChangeCover = true },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(alpha = 0.70f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.60f)),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Change Playlist Photo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(p?.name ?: "", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ZyfenText)
                        Text("${songs.size} songs", style = MaterialTheme.typography.bodySmall, color = ZyfenTextSecondary)
                    }
                    Row {
                        IconButton(onClick = { showChangeCover = true }) {
                            Icon(Icons.Filled.PhotoCamera, "Change Photo", tint = ZyfenTextSecondary)
                        }
                        IconButton(onClick = { renameText = p?.name ?: ""; showRename = true }) {
                            Icon(Icons.Filled.Edit, "Rename", tint = ZyfenTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (songs.isNotEmpty()) {
                        PillButton(
                            "Play All",
                            onClick = {
                                vm.player.playSongs(songs, 0)
                                onOpenPlayer()
                            },
                            icon = { Icon(Icons.Filled.PlayArrow, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                        )
                        PillButton(
                            "Shuffle",
                            onClick = {
                                vm.player.playSongs(songs.shuffled(), 0)
                                onOpenPlayer()
                            },
                            icon = { Icon(Icons.Filled.Shuffle, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        SongRow(
                            song = s,
                            onClick = {
                                vm.player.playSongs(songs, i)
                                onOpenPlayer()
                            },
                            onFav = { vm.toggleFavorite(s) }
                        )
                    }
                    IconButton(onClick = { vm.removeSongFromPlaylist(playlistId, s.id) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = ZyfenTextDisabled, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showChangeCover && p != null) {
        ChangePlaylistCoverDialog(
            playlistId = p.id,
            currentArtworkUrl = p.artworkUrl,
            songArtworks = songs.mapNotNull { it.artworkUri },
            onDismiss = { showChangeCover = false },
            onSaveCover = { newCover ->
                vm.updatePlaylistArtwork(p.id, newCover)
            }
        )
    }

    if (showRename) {
        val accent = LocalAccentColor.current
        GlassDialog(
            onDismissRequest = { showRename = false },
            title = "Rename Playlist",
            confirmButton = {
                GlassPillButton(
                    text = "Save",
                    selected = true,
                    onClick = {
                        if (renameText.isNotBlank()) {
                            vm.renamePlaylist(playlistId, renameText.trim())
                            showRename = false
                        }
                    }
                )
            },
            dismissButton = {
                GlassPillButton(text = "Cancel", onClick = { showRename = false })
            }
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("New Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -------------------------------------------------------------
// HELPERS & SUB-COMPONENTS
// -------------------------------------------------------------
@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Action"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See all", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        } else if (onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun HistoryCard(h: HistoryEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Artwork(
            url = h.artworkUri,
            modifier = Modifier.size(110.dp),
            corner = 16,
            seed = h.songId,
            title = h.title
        )
        Spacer(Modifier.height(6.dp))
        Text(
            h.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            h.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistCard(p: PlaylistEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Artwork(
            url = p.artworkUrl,
            modifier = Modifier.size(120.dp),
            corner = 16,
            seed = p.id,
            title = p.name
        )
        Spacer(Modifier.height(6.dp))
        Text(
            p.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (p.isSpotifyImport) "Spotify" else "Playlist",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    val minutes = diff / 60000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
