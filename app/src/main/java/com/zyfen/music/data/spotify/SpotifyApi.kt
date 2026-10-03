package com.zyfen.music.data.spotify

import com.squareup.moshi.Json
import retrofit2.http.*

// ---- Official Spotify Web API models (public playlist metadata only) ----

data class SpotifyImage(@Json(name = "url") val url: String)
data class SpotifyArtist(@Json(name = "name") val name: String)
data class SpotifyAlbum(
    @Json(name = "name") val name: String,
    @Json(name = "images") val images: List<SpotifyImage> = emptyList()
)
data class SpotifyExternalUrls(@Json(name = "spotify") val spotify: String?)
data class SpotifyTrack(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "artists") val artists: List<SpotifyArtist> = emptyList(),
    @Json(name = "album") val album: SpotifyAlbum? = null,
    @Json(name = "duration_ms") val durationMs: Long = 0,
    @Json(name = "external_urls") val externalUrls: SpotifyExternalUrls? = null,
    @Json(name = "preview_url") val previewUrl: String? = null
)
data class SpotifyPlaylistTrackItem(@Json(name = "track") val track: SpotifyTrack?)
data class SpotifyPlaylistTracksPaging(
    @Json(name = "items") val items: List<SpotifyPlaylistTrackItem> = emptyList(),
    @Json(name = "next") val next: String? = null,
    @Json(name = "total") val total: Int = 0
)
data class SpotifyPlaylist(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "images") val images: List<SpotifyImage> = emptyList(),
    @Json(name = "external_urls") val externalUrls: SpotifyExternalUrls? = null,
    @Json(name = "tracks") val tracks: SpotifyPlaylistTracksPaging
)

data class SpotifyTracksResponse(
    @Json(name = "tracks") val tracks: List<SpotifyTrack?> = emptyList()
)

interface SpotifyService {
    @GET("playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") id: String,
        @Query("fields") fields: String = FIELDS,
        @Query("limit") limit: Int = PAGE_SIZE
    ): SpotifyPlaylist

    @GET
    suspend fun getPlaylistPage(@Url url: String): SpotifyPlaylistTracksPaging

    @GET("tracks")
    suspend fun getTracks(@Query("ids") ids: String): SpotifyTracksResponse

    companion object {
        const val PAGE_SIZE = 100
        const val FIELDS =
            "id,name,description,images,external_urls," +
                "tracks(total,next,items(track(id,name,artists(name),album(name,images(url)),duration_ms,external_urls(spotify))))"
    }
}

object SpotifyLinkParser {
    // Supports open.spotify.com/playlist/{id}, spotify:playlist:{id}
    fun extractPlaylistId(input: String): String? {
        val t = input.trim()
        Regex("spotify:playlist:([A-Za-z0-9]+)").find(t)?.let { return it.groupValues[1] }
        Regex("open\\.spotify\\.com/(?:intl-[a-z-]+/)?playlist/([A-Za-z0-9]+)").find(t)?.let { return it.groupValues[1] }
        Regex("([A-Za-z0-9]{22})").find(t)?.let { return it.groupValues[1] }
        return null
    }
}
