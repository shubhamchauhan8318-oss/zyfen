/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.online.innertube

import com.zyfen.music.data.media.Song
import com.zyfen.music.data.source.TrackIdentity

/**
 * YouTube Music InnerTube search filters matching ViTune's SearchFilter definitions.
 */
enum class InnertubeSearchFilter(val value: String?, val label: String) {
    ALL(null, "All"),
    SONGS("EgWKAQIIAWoOEAMQBBAJEAoQBRAQEBU%3D", "Songs"),
    VIDEOS("EgWKAQIQAWoOEAMQBBAJEAoQBRAQEBU%3D", "Videos"),
    ALBUMS("EgWKAQIYAWoOEAMQBBAJEAoQBRAQEBU%3D", "Albums"),
    ARTISTS("EgWKAQIgAWoOEAMQBBAJEAoQBRAQEBU%3D", "Artists"),
    PLAYLISTS("EgeKAQQoAEABag4QAxAEEAkQChAFEBAQFQ%3D%3D", "Playlists")
}

data class InnertubeThumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

data class InnertubeSongItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumBrowseId: String? = null,
    val durationText: String? = null,
    val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val artworkUrl: String? = null
) {
    /**
     * Converts to Zyfen's canonical Song object with strict YouTube provider identity.
     */
    fun toSong(): Song = Song(
        id = "yt_$videoId",
        title = title,
        artist = artist.ifBlank { "YouTube Music" },
        album = album.ifBlank { "YouTube" },
        durationMs = durationMs,
        contentUri = "yt:$videoId",
        artworkUri = artworkUrl,
        isLocal = false
    )

    /**
     * Converts to Zyfen's canonical TrackIdentity.
     */
    fun toTrackIdentity(): TrackIdentity = TrackIdentity(
        id = "yt_$videoId",
        title = title,
        artist = artist.ifBlank { "YouTube Music" },
        album = album.ifBlank { "YouTube" },
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        sourceProvider = "youtube",
        sourceTrackId = videoId,
        contentUri = "yt:$videoId",
        isLocal = false
    )
}

data class InnertubeVideoItem(
    val videoId: String,
    val title: String,
    val author: String,
    val viewsText: String? = null,
    val durationText: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val isOfficialMusicVideo: Boolean = false
) {
    fun toSong(): Song = Song(
        id = "yt_$videoId",
        title = title,
        artist = author.ifBlank { "YouTube Video" },
        album = if (isOfficialMusicVideo) "Official Music Video" else "YouTube",
        durationMs = durationMs,
        contentUri = "yt:$videoId",
        artworkUri = artworkUrl,
        isLocal = false
    )

    fun toTrackIdentity(): TrackIdentity = TrackIdentity(
        id = "yt_$videoId",
        title = title,
        artist = author.ifBlank { "YouTube Video" },
        album = if (isOfficialMusicVideo) "Official Music Video" else "YouTube",
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        sourceProvider = "youtube",
        sourceTrackId = videoId,
        contentUri = "yt:$videoId",
        isLocal = false
    )
}

data class InnertubeAlbumItem(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val trackCount: Int = 0,
    val artworkUrl: String? = null
)

data class InnertubeArtistItem(
    val browseId: String,
    val name: String,
    val subscribersText: String? = null,
    val artworkUrl: String? = null
)

data class InnertubePlaylistItem(
    val browseId: String,
    val title: String,
    val author: String,
    val songCount: Int = 0,
    val artworkUrl: String? = null
)

data class InnertubeSearchPage(
    val songs: List<InnertubeSongItem> = emptyList(),
    val videos: List<InnertubeVideoItem> = emptyList(),
    val albums: List<InnertubeAlbumItem> = emptyList(),
    val artists: List<InnertubeArtistItem> = emptyList(),
    val playlists: List<InnertubePlaylistItem> = emptyList(),
    val continuationToken: String? = null,
    val hasMore: Boolean = false
)

data class InnertubeArtistPage(
    val browseId: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val topSongs: List<InnertubeSongItem> = emptyList(),
    val albums: List<InnertubeAlbumItem> = emptyList(),
    val singles: List<InnertubeAlbumItem> = emptyList()
)

data class InnertubeAlbumPage(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val artworkUrl: String? = null,
    val songs: List<InnertubeSongItem> = emptyList()
)

data class DirectAudioStream(
    val videoId: String,
    val url: String,
    val expiresAt: Long,
    val itag: Int = 0,
    val mimeType: String = "audio/mp4",
    val bitrate: Long = 0L,
    val source: String = "youtube"
)
