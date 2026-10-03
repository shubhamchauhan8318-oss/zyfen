package com.zyfen.music.data.source

sealed class PlaybackResult {
    data class Success(
        val streamUrl: String,
        val durationMs: Long,
        val format: String = "audio",
        val expiresAt: Long = System.currentTimeMillis() + 6 * 3600_000L,
        val provider: String
    ) : PlaybackResult()

    data class Unavailable(
        val reason: String
    ) : PlaybackResult()
}

data class SearchPageResult(
    val songs: List<TrackIdentity>,
    val artists: List<ArtistResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val playlists: List<PlaylistResult> = emptyList(),
    val continuationToken: String? = null,
    val hasMore: Boolean = false
)

data class ArtistResult(
    val id: String,
    val name: String,
    val artworkUrl: String?,
    val trackCount: Int = 0,
    val description: String? = null
)

data class AlbumResult(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val year: String? = null,
    val trackCount: Int = 0
)

data class PlaylistResult(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val trackCount: Int = 0
)

data class ArtistDetailData(
    val artist: ArtistResult,
    val topSongs: List<TrackIdentity>,
    val albums: List<AlbumResult> = emptyList(),
    val singles: List<AlbumResult> = emptyList(),
    val relatedArtists: List<ArtistResult> = emptyList()
)

interface MusicSource {
    val providerName: String

    suspend fun search(
        query: String,
        filter: String = "all", // "all", "songs", "artists", "albums", "playlists"
        continuationToken: String? = null,
        limit: Int = 25
    ): SearchPageResult

    suspend fun resolvePlayback(identity: TrackIdentity): PlaybackResult

    suspend fun getArtistDetails(artistId: String): ArtistDetailData? = null
}
