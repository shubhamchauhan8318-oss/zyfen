package com.zyfen.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyfen.music.data.media.Song
import com.zyfen.music.ui.components.*
import com.zyfen.music.ui.theme.*

data class AlbumEntry(
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val songs: List<Song>
)

fun buildAlbums(songs: List<Song>): List<AlbumEntry> =
    songs.filter { it.isLocal }
        .groupBy { it.album.ifBlank { "Unknown album" } }
        .map { (album, list) ->
            AlbumEntry(
                name = album,
                artist = list.first().artist,
                artworkUri = list.first().artworkUri,
                songs = list
            )
        }
        .sortedBy { it.name.lowercase() }

private fun playShuffled(vm: LibraryViewModel, list: List<Song>, onOpenPlayer: () -> Unit) {
    if (list.isEmpty()) return
    vm.player.playSongs(list.shuffled(), 0)
    onOpenPlayer()
}

@Composable
fun SongsScreen(vm: LibraryViewModel, onOpenPlayer: () -> Unit, onOpenSearch: () -> Unit = {}) {
    val songs by vm.songs.collectAsState()
    val local = remember(songs) { songs.filter { it.isLocal } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Songs")
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(local, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = {
                            vm.player.playSongs(local, i)
                            onOpenPlayer()
                        },
                        onFav = { vm.toggleFavorite(s) }
                    )
                }
            }
        }
        SearchFab(onClick = onOpenSearch, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun AlbumsScreen(vm: LibraryViewModel, onOpenAlbum: (String) -> Unit, onOpenSearch: () -> Unit = {}) {
    val songs by vm.songs.collectAsState()
    val albums = remember(songs) { buildAlbums(songs) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Albums")
            LazyColumn(Modifier.fillMaxSize()) {
                items(albums, key = { it.name }) { album ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAlbum(album.name) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(album.artworkUri, Modifier.size(88.dp), 8)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                album.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = ZyfenText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = ZyfenTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${album.songs.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZyfenTextSecondary
                            )
                        }
                    }
                }
            }
        }
        SearchFab(onClick = onOpenSearch, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun ArtistsScreen(vm: LibraryViewModel, onOpenArtist: (String) -> Unit, onOpenSearch: () -> Unit = {}) {
    val songs by vm.songs.collectAsState()
    val artists = remember(songs) {
        songs.filter { it.isLocal && it.artist.isNotBlank() }
            .groupBy { it.artist }
            .map { (name, list) -> name to list }
            .sortedBy { it.first.lowercase() }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Artists")
            LazyColumn(Modifier.fillMaxSize()) {
                items(artists, key = { it.first }) { (name, list) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(name) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(list.first().artworkUri, Modifier.size(54.dp), 100)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                color = ZyfenText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${list.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZyfenTextSecondary
                            )
                        }
                    }
                }
            }
        }
        SearchFab(onClick = onOpenSearch, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun DetailHeader(title: String, onBack: () -> Unit) {
    ScreenHeader(
        title = title,
        leading = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = ZyfenText)
            }
        }
    )
}

@Composable
fun AlbumDetailScreen(
    vm: LibraryViewModel,
    albumName: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }
    val songs by vm.songs.collectAsState()
    val album = remember(songs, albumName) { buildAlbums(songs).firstOrNull { it.name == albumName } }
    val list = album?.songs.orEmpty()

    Column(Modifier.fillMaxSize()) {
        DetailHeader(album?.name ?: albumName, onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Artwork(
                    url = album?.artworkUri,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 48.dp),
                    corner = 20,
                    seed = album?.name.orEmpty(),
                    title = album?.name.orEmpty()
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(
                        "Enqueue", onClick = {
                            if (list.isNotEmpty()) {
                                vm.player.playSongs(list, 0); onOpenPlayer()
                            }
                        },
                        icon = { Icon(Icons.Filled.PlaylistAdd, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        "Shuffle", onClick = { playShuffled(vm, list, onOpenPlayer) },
                        icon = { Icon(Icons.Filled.Shuffle, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    song = s,
                    onClick = {
                        vm.player.playSongs(list, i)
                        onOpenPlayer()
                    },
                    onFav = { vm.toggleFavorite(s) }
                )
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    vm: LibraryViewModel,
    artistName: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }
    val songs by vm.songs.collectAsState()
    val list = remember(songs, artistName) {
        songs.filter { it.isLocal && it.artist == artistName }
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(artistName, onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Artwork(
                    list.firstOrNull()?.artworkUri,
                    Modifier.size(180.dp).wrapContentWidth(Alignment.CenterHorizontally),
                    100
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(
                        "Shuffle", onClick = { playShuffled(vm, list, onOpenPlayer) },
                        icon = { Icon(Icons.Filled.Shuffle, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        "Enqueue", onClick = {
                            if (list.isNotEmpty()) {
                                vm.player.playSongs(list, 0); onOpenPlayer()
                            }
                        },
                        icon = { Icon(Icons.Filled.PlaylistAdd, null, tint = ZyfenText, modifier = Modifier.size(18.dp)) }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZyfenText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    song = s,
                    onClick = {
                        vm.player.playSongs(list, i)
                        onOpenPlayer()
                    },
                    onFav = { vm.toggleFavorite(s) }
                )
            }
        }
    }
}
