package com.zyfen.music.ui.nav

import android.net.Uri

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Songs : Route("songs")
    data object Search : Route("search")
    data object Library : Route("library")
    data object Playlists : Route("playlists")
    data object Favorites : Route("favorites")
    data object History : Route("history")
    data object Offline : Route("offline")
    data object Artists : Route("artists")
    data object Albums : Route("albums")
    data object Settings : Route("settings")
    data object NowPlaying : Route("nowplaying")
    data object Queue : Route("queue")
    data object SpotifyImport : Route("spotify_import")
    data object PlaylistDetail : Route("playlist/{id}") {
        fun id(id: String) = "playlist/$id"
    }
    data object AlbumDetail : Route("album/{name}") {
        fun name(name: String) = "album/${Uri.encode(name)}"
    }
    data object ArtistDetail : Route("artist/{name}") {
        fun name(name: String) = "artist/${Uri.encode(name)}"
    }
}
