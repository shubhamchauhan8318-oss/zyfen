package com.zyfen.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.zyfen.music.data.media.Song
import com.zyfen.music.playback.PlayerManager
import com.zyfen.music.ui.theme.*
import kotlin.math.abs

// 8 curated high-end musical gradient palettes
val ArtGradients = listOf(
    listOf(Color(0xFFFF758F), Color(0xFFFFB3C1)), // Rose Blossom
    listOf(Color(0xFF8B5CF6), Color(0xFFC084FC)), // Neon Violet
    listOf(Color(0xFFF97316), Color(0xFFFB923C)), // Sunset Coral
    listOf(Color(0xFF06B6D4), Color(0xFF38BDF8)), // Ocean Cyan
    listOf(Color(0xFF10B981), Color(0xFF34D399)), // Emerald Wave
    listOf(Color(0xFFEC4899), Color(0xFFF472B6)), // Hot Pink
    listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)), // Amber Glow
    listOf(Color(0xFF6366F1), Color(0xFFA855F7))  // Indigo Twilight
)

val ArtIcons = listOf(
    Icons.Filled.Headphones,
    Icons.Filled.Album,
    Icons.Filled.MusicNote,
    Icons.Filled.GraphicEq,
    Icons.Filled.Audiotrack
)

/**
 * High-fidelity album artwork with guaranteed colorful aesthetic fallback for every song.
 * Eliminates blank or missing thumbnails across all audio tracks.
 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Int = 14,
    seed: String = "",
    title: String = "",
    artist: String = ""
) {
    val shape = RoundedCornerShape(corner.dp)
    val hash = abs((seed.ifBlank { title }).hashCode())
    val gradientColors = ArtGradients[hash % ArtGradients.size]
    val icon = ArtIcons[hash % ArtIcons.size]
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"

    var dynamicUrl by remember(url) { mutableStateOf(url) }

    LaunchedEffect(url, title, artist) {
        if (url.isNullOrBlank() && title.isNotBlank()) {
            val resolved = com.zyfen.music.data.online.TrackMetadataResolver.getArtworkUrl(title, artist)
            if (!resolved.isNullOrBlank()) {
                dynamicUrl = resolved
            }
        }
    }

    if (dynamicUrl.isNullOrBlank()) {
        ProceduralCover(
            modifier = modifier,
            shape = shape,
            colors = gradientColors,
            icon = icon,
            initial = initial
        )
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(dynamicUrl)
                .crossfade(true)
                .build(),
            contentDescription = title.ifBlank { "Artwork" },
            modifier = modifier
                .shadow(2.dp, shape = shape, clip = false)
                .clip(shape),
            contentScale = ContentScale.Crop,
            loading = {
                ProceduralCover(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    colors = gradientColors,
                    icon = icon,
                    initial = initial
                )
            },
            error = {
                ProceduralCover(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    colors = gradientColors,
                    icon = icon,
                    initial = initial
                )
            }
        )
    }
}

@Composable
fun ProceduralCover(
    modifier: Modifier,
    shape: RoundedCornerShape,
    colors: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    initial: String
) {
    Box(
        modifier = modifier
            .shadow(3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        // Inner subtle disc vinyl rings
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.65f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.55f)
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (leading != null) {
            Box(Modifier.align(Alignment.CenterStart)) { leading() }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                shadow = LiquidGlassTokens.TextShadow
            ),
            color = Color.White,
            modifier = Modifier
                .align(if (leading == null) Alignment.CenterStart else Alignment.Center)
                .padding(horizontal = if (leading != null) 48.dp else 0.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val accent = LocalAccentColor.current
    Surface(
        modifier = modifier
            .shadow(6.dp, shape = RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = LiquidGlassTokens.SubtleTextShadow
                ),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/**
 * Premium track item row matching Liquid Glassmorphism design rules.
 * Shows track number, guaranteed colorful artwork, title, artist, favorite heart, and options menu.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onFav: () -> Unit = {},
    trackNumber: String? = null,
    onMore: (() -> Unit)? = null
) {
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.07f),
                        Color.White.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional track number (e.g. "04")
            if (!trackNumber.isNullOrBlank()) {
                Text(
                    text = trackNumber,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        shadow = LiquidGlassTokens.SubtleTextShadow
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.width(28.dp)
                )
            }

            // Guaranteed colorful artwork thumbnail
            Artwork(
                url = song.artworkUri,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.45f)),
                corner = 12,
                seed = song.id,
                title = song.title,
                artist = song.artist
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = LiquidGlassTokens.SubtleTextShadow
                    ),
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = LiquidGlassTokens.SubtleTextShadow
                        ),
                        color = Color(0xFFD8B4FE),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val dur = formatDuration(song.durationMs)
                    if (dur.isNotEmpty()) {
                        Text(
                            text = " • $dur",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.60f)
                        )
                    }
                }
            }

            IconButton(onClick = onFav, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) accent else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact mini player floating above the bottom navigation bar with Liquid Glassmorphism styling.
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progress: Float,
    preparing: Boolean = false,
    stage: String = "",
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    if (song == null) return
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(24.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.50f),
                ambientColor = Color.White.copy(alpha = 0.08f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color(0xFF16102D).copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape
            )
            .clickable(onClick = onOpen),
        shape = shape,
        color = Color.Transparent
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    url = song.artworkUri,
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    corner = 12,
                    seed = song.id,
                    title = song.title
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            shadow = LiquidGlassTokens.SubtleTextShadow
                        ),
                        color = Color.White
                    )
                    Text(
                        text = if (preparing && stage.isNotBlank()) stage else song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = LiquidGlassTokens.SubtleTextShadow
                        ),
                        color = Color(0xFFD8B4FE)
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(42.dp)) {
                    if (preparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.28f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (preparing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = accent,
                    trackColor = Color.Transparent
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = accent,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun ConnectedMiniPlayer(player: PlayerManager, onOpen: () -> Unit) {
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val pos by player.position.collectAsState()
    val dur by player.duration.collectAsState()
    val preparing by player.isPreparing.collectAsState()
    val stage by player.resolveStage.collectAsState()
    MiniPlayer(
        song = song,
        isPlaying = isPlaying,
        progress = if (dur > 0) pos.toFloat() / dur else 0f,
        preparing = preparing,
        stage = stage,
        onToggle = { player.togglePlayPause() },
        onNext = { player.next() },
        onOpen = onOpen
    )
}

@Composable
fun SearchFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccentColor.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accent.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.40f)),
        modifier = modifier
            .padding(24.dp)
            .size(56.dp)
            .shadow(12.dp, CircleShape, spotColor = accent.copy(alpha = 0.6f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Search, "Search", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

