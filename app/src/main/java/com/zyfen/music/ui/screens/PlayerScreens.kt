package com.zyfen.music.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.zyfen.music.data.media.Song
import com.zyfen.music.playback.AudioEffectsManager
import com.zyfen.music.playback.PlayerManager
import com.zyfen.music.ui.components.AddToPlaylistSheet
import com.zyfen.music.ui.components.Artwork
import com.zyfen.music.ui.components.EqualizerReverbSheet
import com.zyfen.music.ui.components.ScreenHeader
import com.zyfen.music.ui.theme.LocalAccentColor
import com.zyfen.music.ui.theme.LocalUIStyle
import com.zyfen.music.ui.theme.ZyfenNeon
import com.zyfen.music.ui.theme.ZyfenTextDisabled
import com.zyfen.music.ui.theme.ZyfenTextSecondary
import com.zyfen.music.ui.theme.rememberDynamicPalette
import kotlin.math.roundToLong

@Composable
fun NowPlayingScreen(
    player: PlayerManager,
    vm: LibraryViewModel,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }

    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val pos by player.position.collectAsState()
    val dur by player.duration.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeat by player.repeat.collectAsState()
    val queue by player.queue.collectAsState()
    val queueIdx by player.currentIndex.collectAsState()
    val volume by player.volume.collectAsState()
    val songs by vm.songs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val isFavorite = song != null && songs.any { it.id == song?.id && it.isFavorite }
    val preparing by player.isPreparing.collectAsState()
    val stage by player.resolveStage.collectAsState()
    val uiStyle = LocalUIStyle.current
    val settingsStore = remember { com.zyfen.music.ZyfenApp.container.settingsStore }
    val dynamicArtwork by settingsStore.dynamicArtwork.collectAsState(initial = false)

    var showLyrics by remember { mutableStateOf(false) }
    var showQueueInline by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showEqReverb by remember { mutableStateOf(false) }

    if (uiStyle == "glass") {
        GlassmorphismNowPlayingContent(
            song = song,
            isPlaying = isPlaying,
            pos = pos,
            dur = dur,
            shuffle = shuffle,
            repeat = repeat,
            queue = queue,
            queueIdx = queueIdx,
            volume = volume,
            onVolumeChange = { player.setVolume(it) },
            dynamicArtworkEnabled = dynamicArtwork,
            isFavorite = isFavorite,
            preparing = preparing,
            stage = stage,
            showLyrics = showLyrics,
            showQueueInline = showQueueInline,
            onToggleLyrics = { showLyrics = !showLyrics; if (showLyrics) showQueueInline = false },
            onToggleQueueInline = { showQueueInline = !showQueueInline; if (showQueueInline) showLyrics = false },
            onSeek = { player.seekTo(it) },
            onToggle = { player.togglePlayPause() },
            onNext = { player.next() },
            onPrev = { player.previous() },
            onShuffle = { player.toggleShuffle() },
            onRepeat = { player.cycleRepeat() },
            onFav = { song?.let { vm.toggleFavorite(it) } },
            onRetry = { player.retryCurrentSong() },
            onPlayQueueItem = { idx -> player.playSongs(queue, idx) },
            onOpenQueue = onOpenQueue,
            onOpenAddToPlaylist = { showAddToPlaylist = true },
            onOpenEqReverb = { showEqReverb = true },
            onBack = onBack
        )
    } else {
        NowPlayingContent(
            song = song,
            isPlaying = isPlaying,
            pos = pos,
            dur = dur,
            shuffle = shuffle,
            repeat = repeat,
            queue = queue,
            queueIdx = queueIdx,
            isFavorite = isFavorite,
            preparing = preparing,
            stage = stage,
            showLyrics = showLyrics,
            showQueueInline = showQueueInline,
            onToggleLyrics = { showLyrics = !showLyrics; if (showLyrics) showQueueInline = false },
            onToggleQueueInline = { showQueueInline = !showQueueInline; if (showQueueInline) showLyrics = false },
            onSeek = { player.seekTo(it) },
            onToggle = { player.togglePlayPause() },
            onNext = { player.next() },
            onPrev = { player.previous() },
            onShuffle = { player.toggleShuffle() },
            onRepeat = { player.cycleRepeat() },
            onFav = { song?.let { vm.toggleFavorite(it) } },
            onRetry = { player.retryCurrentSong() },
            onPlayQueueItem = { idx -> player.playSongs(queue, idx) },
            onOpenQueue = onOpenQueue,
            onOpenAddToPlaylist = { showAddToPlaylist = true },
            onOpenEqReverb = { showEqReverb = true },
            onBack = onBack
        )
    }

    if (showAddToPlaylist && song != null) {
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onCreatePlaylist = { vm.createPlaylist(it) },
            onAddToPlaylist = { pid, s -> vm.addToPlaylist(pid, s) },
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showEqReverb) {
        EqualizerReverbSheet(
            onDismiss = { showEqReverb = false }
        )
    }
}

/**
 * Liquid Glassmorphism Now Playing Screen matching Image 2 reference.
 * Features twilight aurora gradient backdrop, frosted translucent glass card container,
 * large rounded artwork with corner favorite badge, sleek seekbar, glass pill tool capsule, and volume control.
 */
@Composable
fun GlassmorphismNowPlayingContent(
    song: Song?,
    isPlaying: Boolean,
    pos: Long,
    dur: Long,
    shuffle: Boolean,
    repeat: Int,
    queue: List<Song> = emptyList(),
    queueIdx: Int = 0,
    volume: Float = 1.0f,
    onVolumeChange: (Float) -> Unit = {},
    dynamicArtworkEnabled: Boolean = false,
    showLyrics: Boolean = false,
    showQueueInline: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onToggleQueueInline: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggle: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onFav: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPlayQueueItem: (Int) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenAddToPlaylist: () -> Unit = {},
    onOpenEqReverb: () -> Unit = {},
    isFavorite: Boolean = false,
    preparing: Boolean = false,
    stage: String = "",
    onBack: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    val palette = rememberDynamicPalette(song, dynamicArtworkEnabled, LocalAccentColor.current)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.backgroundTop,
                        palette.backgroundMid,
                        palette.primary.copy(alpha = 0.45f),
                        palette.secondary.copy(alpha = 0.25f),
                        palette.backgroundBottom
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Floating Frosted Glass Card Container (Reference Image 2)
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.09f)),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        palette.borderGlow,
                        Color.White.copy(alpha = 0.35f),
                        palette.borderGlow.copy(alpha = 0.15f)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Glass Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = { menuOpen = false; onOpenAddToPlaylist() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Equalizer & Reverb") },
                                onClick = { menuOpen = false; onOpenEqReverb() },
                                leadingIcon = { Icon(Icons.Filled.Equalizer, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showLyrics) "Hide Lyrics" else "Show Lyrics") },
                                onClick = { menuOpen = false; onToggleLyrics() },
                                leadingIcon = { Icon(Icons.Filled.Lyrics, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Up Next Queue") },
                                onClick = { menuOpen = false; onOpenQueue() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
                            )
                        }
                    }
                }

                // 2. Center Content Area: Artwork with Floating Heart OR Lyrics OR Queue
                when {
                    showLyrics -> {
                        LyricsView(
                            song = song,
                            pos = pos,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    showQueueInline -> {
                        InlineQueueView(
                            queue = queue,
                            currentIndex = queueIdx,
                            onPlayItem = onPlayQueueItem,
                            onOpenFullQueue = onOpenQueue,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Large square rounded artwork with floating heart badge (Image 2)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Artwork(
                                    url = song?.artworkUri,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(26.dp))
                                        .shadow(16.dp, RoundedCornerShape(26.dp)),
                                    corner = 26,
                                    seed = song?.id.orEmpty(),
                                    title = song?.title.orEmpty()
                                )

                                // Floating circular frosted heart badge (Image 2)
                                Surface(
                                    onClick = onFav,
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFavorite) Color(0xFFFF2A6D) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Song Title & Artist
                            Text(
                                text = song?.title ?: "No track playing",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = song?.artist ?: "Select a track",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = Color(0xFFE2D4EC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            if (preparing) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stage.ifBlank { "Buffering high quality audio…" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Smooth Butter-Seeker Slider
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                val currentProgress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                val sliderValue = if (isDragging) dragProgress else currentProgress
                val displayPos = if (isDragging) (dragProgress * dur).roundToLong() else pos

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDragging = true
                            dragProgress = it
                        },
                        onValueChangeFinished = {
                            onSeek((dragProgress * dur).roundToLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fmt(displayPos), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 4. Playback Controls Row: Shuffle, Prev, Frosted Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            "Shuffle",
                            tint = if (shuffle) palette.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = onPrev) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Centered Frosted Play/Pause Circle (Image 2)
                    Surface(
                        onClick = onToggle,
                        shape = CircleShape,
                        color = palette.primary.copy(alpha = 0.32f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.borderGlow),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (preparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Filled.SkipNext,
                            "Next",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(onClick = onRepeat) {
                        Icon(
                            Icons.Filled.Repeat,
                            "Repeat",
                            tint = if (repeat != Player.REPEAT_MODE_OFF) palette.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 5. Frosted Glass Tool Capsule (Queue, Equalizer, Reverb, AddToPlaylist - Image 2)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    val isReverbOn by AudioEffectsManager.isReverbEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onOpenEqReverb) {
                            Icon(Icons.Filled.Equalizer, "Equalizer", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onOpenEqReverb) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                "Reverb",
                                tint = if (isReverbOn) palette.primary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onOpenAddToPlaylist) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 6. Volume Slider Row (Image 2)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (volume > 0f) onVolumeChange(0f) else onVolumeChange(0.75f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeMute,
                            "Mute",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = { onVolumeChange(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(
                        onClick = { onVolumeChange(1.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            "Max Volume",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Now Playing Screen recreating Screen A from the reference image.
 * Features soft pastel blush canvas, large rounded album artwork with soft shadow,
 * refined progress slider, large circular play/pause button, and expandable Up Next section.
 */
@Composable
fun NowPlayingContent(
    song: Song?,
    isPlaying: Boolean,
    pos: Long,
    dur: Long,
    shuffle: Boolean,
    repeat: Int,
    queue: List<Song> = emptyList(),
    queueIdx: Int = 0,
    showLyrics: Boolean = false,
    showQueueInline: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onToggleQueueInline: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggle: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onFav: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPlayQueueItem: (Int) -> Unit = {},
    onOpenAddToPlaylist: () -> Unit = {},
    onOpenEqReverb: () -> Unit = {},
    isFavorite: Boolean = false,
    preparing: Boolean = false,
    stage: String = "",
    onOpenQueue: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val progress = if (dur > 0) pos.toFloat() / dur else 0f
    val accent = LocalAccentColor.current
    val nextSong = queue.getOrNull(queueIdx + 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Header: Back, ALBUM label & Album Name, and Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ALBUM",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = song?.album?.ifBlank { "Now Playing" } ?: "Now Playing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        imageVector = Icons.Filled.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onToggleQueueInline) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (showQueueInline) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Center Content Area: Large Album Art OR Lyrics OR Inline Queue
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                showLyrics -> {
                    LyricsView(
                        song = song,
                        pos = pos,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                showQueueInline -> {
                    InlineQueueView(
                        queue = queue,
                        currentIndex = queueIdx,
                        onPlayItem = onPlayQueueItem,
                        onOpenFullQueue = onOpenQueue,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large square album artwork container with rounded corners (~28dp) & soft drop shadow
                        Artwork(
                            url = song?.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(14.dp, RoundedCornerShape(28.dp), clip = false),
                            corner = 28,
                            seed = song?.id.orEmpty(),
                            title = song?.title.orEmpty()
                        )

                        Spacer(Modifier.height(28.dp))

                        // Song Title & Artist
                        Text(
                            text = song?.title ?: "No track playing",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song?.artist ?: "Select a song to start",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        if (preparing) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
                                Text(
                                    text = stage.ifBlank { "Connecting 320kbps audio…" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        } else if (!isPlaying && song != null && !song.isLocal && pos == 0L) {
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, null, tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry Stream", color = accent, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Progress Bar & Duration (Smooth non-blocking drag)
        var isClassicDragging by remember { mutableStateOf(false) }
        var classicDragProgress by remember { mutableFloatStateOf(0f) }
        val classicSliderVal = if (isClassicDragging) classicDragProgress else progress.coerceIn(0f, 1f)
        val classicDisplayPos = if (isClassicDragging) (classicDragProgress * dur).roundToLong() else pos

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            Slider(
                value = classicSliderVal,
                onValueChange = {
                    isClassicDragging = true
                    classicDragProgress = it
                },
                onValueChangeFinished = {
                    onSeek((classicDragProgress * dur).roundToLong())
                    isClassicDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fmt(classicDisplayPos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = fmt(dur),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Playback Controls Row: Previous, Large Circular Play/Pause Button, Next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Large Circular Play/Pause Button with soft blush glow circle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (preparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Secondary Controls Row: Shuffle, Repeat, Share, Favorite
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onRepeat) {
                Icon(
                    imageVector = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = {
                song?.let { s ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listening to '${s.title}' by ${s.artist} on Zyfen Music!")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Music"))
                }
            }) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onFav) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Expandable "Up Next" section matching the reference design
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .clickable(onClick = onOpenQueue),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = nextSong?.title ?: "Queue is empty",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Open Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun InlineQueueView(
    queue: List<Song>,
    currentIndex: Int,
    onPlayItem: (Int) -> Unit,
    onOpenFullQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Up Next (${queue.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onOpenFullQueue) {
                Text("Manage Queue", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", color = ZyfenTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(queue) { idx, s ->
                    val isCurrent = idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { onPlayItem(idx) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            url = s.artworkUri,
                            modifier = Modifier.size(44.dp),
                            corner = 10,
                            seed = s.id,
                            title = s.title
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                s.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZyfenTextSecondary
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                "Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                fmt(s.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZyfenTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsView(
    song: Song?,
    pos: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lyrics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Lyrics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Real-time synchronized lyrics will appear here when available for '${song?.title ?: "this song"}'.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZyfenTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QueueScreen(
    player: PlayerManager,
    lib: LibraryViewModel,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }

    val queue by player.queue.collectAsState()
    val curIdx by player.currentIndex.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(
            title = "Playback Queue (${queue.size})",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
            actions = {
                if (queue.isNotEmpty()) {
                    IconButton(onClick = { player.playSongs(emptyList(), 0) }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear Queue", tint = ZyfenTextSecondary)
                    }
                }
            }
        )

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QueueMusic, null, tint = ZyfenTextDisabled, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Your queue is empty", color = ZyfenTextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(queue) { idx, s ->
                    val isCurrent = idx == curIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { player.playSongs(queue, idx) }
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            url = s.artworkUri,
                            modifier = Modifier.size(48.dp),
                            corner = 10,
                            seed = s.id,
                            title = s.title
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                s.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZyfenTextSecondary
                            )
                        }
                        IconButton(onClick = {
                            val updated = queue.toMutableList().apply { removeAt(idx) }
                            val newStart = if (idx < curIdx) (curIdx - 1).coerceAtLeast(0)
                            else curIdx.coerceAtMost((updated.size - 1).coerceAtLeast(0))
                            player.playSongs(updated, newStart)
                        }) {
                            Icon(Icons.Filled.Close, "Remove", tint = ZyfenTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
