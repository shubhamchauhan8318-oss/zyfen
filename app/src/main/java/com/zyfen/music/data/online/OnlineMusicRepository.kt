/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.online

import android.util.Log
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.online.innertube.*
import com.zyfen.music.data.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Enterprise-grade Online Music Repository backed by ViTune's InnerTube architecture.
 *
 * Guarantees:
 * 1. Stable, canonical provider identity (provider="youtube", videoId=11-char ID).
 * 2. Complete multi-category search: Songs, Videos, Albums, Artists, Playlists.
 * 3. Exact artist catalog explorer & discography parser.
 * 4. Pagination via continuation tokens.
 * 5. High-resolution artwork selection directly bound to track identity.
 */
class OnlineMusicRepository(
    private val client: OkHttpClient,
    val innertube: InnertubeClient = InnertubeClient(client)
) {

    companion object {
        private const val TAG = "OnlineMusicRepo"
    }

    /**
     * Backward-compatible simple search returning Songs with canonical YouTube identities.
     */
    suspend fun searchSongs(query: String, limit: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        val result = search(query, filter = "songs", limit = limit)
        result.songs.map { it.toSong() }
    }

    /**
     * Live search auto-completion suggestions.
     */
    suspend fun getSuggestions(query: String): List<String> =
        innertube.getSearchSuggestions(query)

    /**
     * Full Paginated & Categorized Search Engine.
     */
    suspend fun search(
        query: String,
        filter: String = "all", // "all", "songs", "videos", "artists", "albums", "playlists"
        continuationToken: String? = null,
        limit: Int = 35
    ): SearchPageResult = withContext(Dispatchers.IO) {
        if (query.isBlank() && continuationToken.isNullOrBlank()) {
            return@withContext SearchPageResult(emptyList())
        }

        val innertubeFilter = when (filter.lowercase()) {
            "songs" -> InnertubeSearchFilter.SONGS
            "videos" -> InnertubeSearchFilter.VIDEOS
            "albums" -> InnertubeSearchFilter.ALBUMS
            "artists" -> InnertubeSearchFilter.ARTISTS
            "playlists" -> InnertubeSearchFilter.PLAYLISTS
            else -> InnertubeSearchFilter.ALL
        }

        val page = innertube.search(query, innertubeFilter, continuationToken)

        // Map songs & videos with exact canonical provider identity
        val songIdentities = ArrayList<TrackIdentity>()

        // 1. Process Song items
        for (item in page.songs) {
            val identity = item.toTrackIdentity()
            item.artworkUrl?.let { ArtworkCache.put("youtube", item.videoId, it) }
            songIdentities.add(identity)
        }

        // 2. Process Video items (if searching videos or all)
        for (item in page.videos) {
            val identity = item.toTrackIdentity()
            item.artworkUrl?.let { ArtworkCache.put("youtube", item.videoId, it) }
            songIdentities.add(identity)
        }

        // 3. Process Albums
        val albumResults = page.albums.map { item ->
            AlbumResult(
                id = item.browseId,
                title = item.title,
                artist = item.artist,
                artworkUrl = item.artworkUrl,
                year = item.year,
                trackCount = item.trackCount
            )
        }

        // 4. Process Artists
        val artistResults = page.artists.map { item ->
            ArtistResult(
                id = item.browseId,
                name = item.name,
                artworkUrl = item.artworkUrl,
                description = item.subscribersText
            )
        }

        // 5. Process Playlists
        val playlistResults = page.playlists.map { item ->
            PlaylistResult(
                id = item.browseId,
                title = item.title,
                author = item.author,
                artworkUrl = item.artworkUrl,
                trackCount = item.songCount
            )
        }

        Log.i(TAG, "Search '$query' ($filter) mapped: ${songIdentities.size} tracks, ${artistResults.size} artists, ${albumResults.size} albums")

        SearchPageResult(
            songs = songIdentities.take(limit),
            artists = artistResults,
            albums = albumResults,
            playlists = playlistResults,
            continuationToken = page.continuationToken,
            hasMore = page.hasMore
        )
    }

    /**
     * Retrieves artist detailed discography (Top Songs, Albums, Singles).
     */
    suspend fun getArtistDetails(artistBrowseId: String): ArtistDetailData? = withContext(Dispatchers.IO) {
        val page = innertube.getArtistPage(artistBrowseId) ?: return@withContext null

        val topSongs = page.topSongs.map { item ->
            val identity = item.toTrackIdentity()
            item.artworkUrl?.let { ArtworkCache.put("youtube", item.videoId, it) }
            identity
        }

        val albums = page.albums.map { item ->
            AlbumResult(
                id = item.browseId,
                title = item.title,
                artist = item.artist,
                artworkUrl = item.artworkUrl,
                year = item.year
            )
        }

        val singles = page.singles.map { item ->
            AlbumResult(
                id = item.browseId,
                title = item.title,
                artist = item.artist,
                artworkUrl = item.artworkUrl,
                year = item.year
            )
        }

        ArtistDetailData(
            artist = ArtistResult(
                id = page.browseId,
                name = page.name,
                artworkUrl = page.artworkUrl,
                description = page.description
            ),
            topSongs = topSongs,
            albums = albums,
            singles = singles
        )
    }
}
