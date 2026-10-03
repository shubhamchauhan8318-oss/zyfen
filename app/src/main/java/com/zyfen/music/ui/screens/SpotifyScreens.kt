package com.zyfen.music.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyfen.music.ZyfenApp
import com.zyfen.music.ui.components.Artwork
import com.zyfen.music.ui.components.ScreenHeader
import com.zyfen.music.ui.components.SongRow

@Composable
fun SpotifyImportScreen(
    vm: SpotifyViewModel = remember { SpotifyViewModel(ZyfenApp.container.spotifyRepo) },
    lib: LibraryViewModel,
    onBack: () -> Unit = {}
) {
    androidx.activity.compose.BackHandler { onBack() }
    var link by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    val res by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val mIdx by lib.matchIndex.collectAsState()
    val previewSongs = remember(res) {
        res?.tracks.orEmpty().mapIndexed { i, t ->
            com.zyfen.music.data.media.Song(
                id = "spotify_${t.id ?: "loc_$i"}",
                title = t.name,
                artist = t.artists.joinToString(", ") { it.name },
                album = t.album?.name ?: "",
                durationMs = t.durationMs,
                contentUri = "",
                artworkUri = t.album?.images?.firstOrNull()?.url,
                isLocal = false,
                spotifyUrl = t.externalUrls?.spotify
            )
        }
    }
    val (_, aligned) = remember(previewSongs, mIdx) {
        com.zyfen.music.ui.components.SongMatch.build(previewSongs, mIdx)
    }
    val queue = remember(previewSongs, aligned) {
        previewSongs.mapIndexed { j, x -> aligned.getOrNull(j) ?: x }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                "Spotify Import",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = com.zyfen.music.ui.theme.ZyfenText)
                    }
                }
            )
            Text(
                "Paste any public Spotify playlist link — no account, no Client ID/Secret needed. " +
                    "Playlist details are imported from the official Spotify API (metadata only, audio is never downloaded).",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                link, { link = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Spotify playlist link") },
                placeholder = { Text("https://open.spotify.com/playlist/...") }, singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.import(link) },
                enabled = !busy && link.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(progress ?: "Importing…")
                } else Text("Import playlist")
            }
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
        res?.let { r ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(r.artworkUrl, Modifier.size(72.dp), 14)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append("${r.tracks.size} tracks imported")
                                    if (r.skipped > 0) append(" · ${r.skipped} skipped")
                                },
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (r.truncated) "(first ${r.tracks.size} songs — Spotify embed limit). " else "") +
                        "Har track ZYFEN me chalega — device file hogi to offline, warna online stream hoga.",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
                LaunchedEffect(r) { lib.refresh() }
            }
            itemsIndexed(r.tracks, key = { i, t -> "tr_${t.id ?: "loc_$i"}_$i" }) { i, t ->
                val s = previewSongs.getOrNull(i) ?: return@itemsIndexed
                SongRow(
                    song = s,
                    onClick = {
                        lib.player.playSongs(queue, i)
                    },
                    onFav = { }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Settings",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = com.zyfen.music.ui.theme.ZyfenText)
                }
            }
        )
        Column(Modifier.padding(20.dp)) {
            Text("ZYFEN MUSIC 1.0.0 — offline-first, premium dark player.", color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text(
                "Spotify Import: paste a playlist link — no account or developer keys needed. " +
                    "Sirf metadata import hota hai (Spotify audio kabhi download nahi hota). " +
                    "Gaane chalte hain: device file ho to offline, warna online stream hote hain.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
