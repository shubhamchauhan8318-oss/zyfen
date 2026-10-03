package com.zyfen.music.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyfen.music.data.local.*
import com.zyfen.music.data.media.*
import com.zyfen.music.playback.PlayerManager
import com.zyfen.music.ui.components.SongIndex
import com.zyfen.music.ui.components.SongMatch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LibraryViewModel(
    private val localRepo: LocalMusicRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    val player: PlayerManager,
    val downloader: com.zyfen.music.data.download.SongDownloader = com.zyfen.music.ZyfenApp.container.songDownloader
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val history = _history.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _detail = MutableStateFlow<PlaylistWithSongs?>(null)
    val detail = _detail.asStateFlow()

    private val _matchIndex = MutableStateFlow(SongIndex(emptyList()))
    val matchIndex = _matchIndex.asStateFlow()

    val filtered: StateFlow<List<Song>> = combine(_songs, _query) { songs, q ->
        if (q.isBlank()) songs else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        try {
            val local = localRepo.loadLocalSongs()
            // merge favorites flags from DB
            val dbSongs = songDao.allSongs().associateBy { it.id }
            val merged = local.map { s ->
                val fav = dbSongs[s.id]?.isFavorite ?: false
                s.copy(isFavorite = fav)
            }
            // cache local songs in DB (for playlists/favs)
            songDao.upsertAll(merged.map { it.toEntity() })
            _matchIndex.value = SongIndex(merged)
            val spotifyCached = songDao.allSongs().filter { !it.isLocal }.map { it.toSong() }
            _songs.value = merged + spotifyCached
            _favorites.value = songDao.favorites().map { it.toSong() }
            _playlists.value = playlistDao.allPlaylists()
            _history.value = songDao.getRecentHistory()
        } catch (_: Exception) {
        }
        _loading.value = false
    }

    fun setQuery(q: String) { _query.value = q }

    fun toggleFavorite(song: Song) = viewModelScope.launch {
        val v = !song.isFavorite
        val updated = song.copy(isFavorite = v)
        songDao.upsert(updated.toEntity())
        songDao.setFavorite(song.id, v)
        _songs.value = _songs.value.map { if (it.id == song.id) updated else it }
        _favorites.value = songDao.favorites().map { it.toSong() }

        if (v) {
            // User requested: Whenever any song is liked, automatically download it for offline use!
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    downloader.download(updated)
                    val dbSong = songDao.byId(song.id)
                    if (dbSong != null) {
                        val fresh = dbSong.toSong()
                        _songs.value = _songs.value.map { if (it.id == song.id) fresh else it }
                        _favorites.value = _favorites.value.map { if (it.id == song.id) fresh else it }
                    }
                }
            }
        }
    }

    fun updatePlaylistArtwork(id: String, artworkUrl: String?) = viewModelScope.launch {
        playlistDao.updatePlaylistArtwork(id, artworkUrl)
        _playlists.value = playlistDao.allPlaylists()
        loadPlaylist(id)
    }

    fun createPlaylist(name: String) = viewModelScope.launch {
        playlistDao.upsert(PlaylistEntity(id = UUID.randomUUID().toString(), name = name, artworkUrl = null))
        _playlists.value = playlistDao.allPlaylists()
    }

    fun renamePlaylist(id: String, newName: String) = viewModelScope.launch {
        playlistDao.renamePlaylist(id, newName)
        _playlists.value = playlistDao.allPlaylists()
        loadPlaylist(id)
    }

    fun deletePlaylist(id: String) = viewModelScope.launch {
        playlistDao.delete(id)
        _playlists.value = playlistDao.allPlaylists()
    }

    fun addToPlaylist(playlistId: String, song: Song) = viewModelScope.launch {
        songDao.upsert(song.toEntity())
        val existing = playlistDao.withSongs(playlistId)?.songs?.size ?: 0
        playlistDao.addSong(PlaylistSongCrossRef(playlistId, song.id, existing))
        _playlists.value = playlistDao.allPlaylists()
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) = viewModelScope.launch {
        playlistDao.removeSong(playlistId, songId)
        loadPlaylist(playlistId)
    }

    fun clearHistory() = viewModelScope.launch {
        songDao.clearHistory()
        _history.value = emptyList()
    }

    fun deleteHistoryItem(id: Long) = viewModelScope.launch {
        songDao.deleteHistoryItem(id)
        _history.value = songDao.getRecentHistory()
    }

    fun playAll(startIndex: Int = 0) {
        val list = filtered.value.ifEmpty { _songs.value }
        if (list.isEmpty()) return
        val aligned = SongMatch.build(list, _matchIndex.value).second
        val queue = list.mapIndexed { i, s -> aligned.getOrNull(i) ?: s }
        player.playSongs(queue, startIndex.coerceIn(queue.indices))
    }

    fun loadPlaylist(id: String) = viewModelScope.launch {
        val currentDetail = playlistDao.withSongs(id)
        _detail.value = currentDetail

        // Automatically resolve missing cover art & real artists for all tracks in playlist
        if (currentDetail != null && currentDetail.songs.isNotEmpty()) {
            val needsEnrichment = currentDetail.songs.filter {
                it.artworkUri.isNullOrBlank() ||
                it.artist.contains("zyfen", ignoreCase = true) ||
                it.artist.contains("spotify", ignoreCase = true) ||
                it.artist.equals("Unknown Artist", ignoreCase = true)
            }
            if (needsEnrichment.isNotEmpty()) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    var updatedAny = false
                    for (entity in needsEnrichment) {
                        val meta = com.zyfen.music.data.online.TrackMetadataResolver.resolve(entity.title, entity.artist)
                        if (meta != null) {
                            val newArt = meta.artworkUrl ?: entity.artworkUri
                            val newArtist = meta.artist.ifBlank { entity.artist }
                            val newTitle = meta.title.ifBlank { entity.title }
                            if (newArt != entity.artworkUri || newArtist != entity.artist || newTitle != entity.title) {
                                songDao.updateMetadata(entity.id, newTitle, newArtist, newArt)
                                updatedAny = true
                            }
                        }
                    }
                    if (updatedAny) {
                        _detail.value = playlistDao.withSongs(id)
                    }
                }
            }
        }
    }

    fun playPlaylist(id: String, startIndex: Int = 0) {
        val songs = _detail.value?.songs.orEmpty().map { it.toSong() }
        if (songs.isEmpty()) return
        val aligned = SongMatch.build(songs, _matchIndex.value).second
        val queue = songs.mapIndexed { i, s -> aligned.getOrNull(i) ?: s }
        player.playSongs(queue, startIndex.coerceIn(queue.indices))
    }
}
