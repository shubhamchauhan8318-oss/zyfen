/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.online.innertube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class InnertubeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "InnertubeClient"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val MUSIC_BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com/youtubei/v1"

        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private fun countryCode(): String =
        Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private fun languageCode(): String =
        Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"

    // -------------------------------------------------------------
    // SEARCH AUTO-SUGGESTIONS
    // -------------------------------------------------------------
    suspend fun getSearchSuggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext emptyList()
        try {
            val body = JSONObject()
                .put("context", makeContext("WEB_REMIX", "1.20241001.01.00"))
                .put("input", input.trim())

            val url = "$MUSIC_BASE_URL/music/get_search_suggestions?prettyPrint=false"
            val resp = postJson(url, body.toString(), "https://music.youtube.com/") ?: return@withContext emptyList()
            val root = JSONObject(resp)
            val suggestions = mutableListOf<String>()

            fun scanSuggestions(node: Any?) {
                when (node) {
                    is JSONObject -> {
                        if (node.has("searchSuggestionRenderer")) {
                            val r = node.getJSONObject("searchSuggestionRenderer")
                            val runs = r.optJSONObject("suggestion")?.optJSONArray("runs")
                            if (runs != null) {
                                val sb = StringBuilder()
                                for (i in 0 until runs.length()) {
                                    sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
                                }
                                val text = sb.toString().trim()
                                if (text.isNotBlank()) suggestions.add(text)
                            }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) scanSuggestions(node.opt(keys.next()))
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) scanSuggestions(node.opt(i))
                    }
                }
            }
            scanSuggestions(root)
            suggestions.distinct().take(10)
        } catch (e: Exception) {
            Log.w(TAG, "getSearchSuggestions error: ${e.message}")
            emptyList()
        }
    }

    // -------------------------------------------------------------
    // MULTI-CATEGORY SEARCH & PAGINATION
    // -------------------------------------------------------------
    suspend fun search(
        query: String,
        filter: InnertubeSearchFilter = InnertubeSearchFilter.ALL,
        continuationToken: String? = null
    ): InnertubeSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank() && continuationToken.isNullOrBlank()) {
            return@withContext InnertubeSearchPage()
        }

        try {
            val body = JSONObject().put("context", makeContext("WEB_REMIX", "1.20241001.01.00"))
            val url: String
            if (!continuationToken.isNullOrBlank()) {
                url = "$MUSIC_BASE_URL/search?continuation=$continuationToken&prettyPrint=false"
                body.put("continuation", continuationToken)
            } else {
                url = "$MUSIC_BASE_URL/search?prettyPrint=false"
                body.put("query", query.trim())
                filter.value?.let { body.put("params", it) }
            }

            val resp = postJson(url, body.toString(), "https://music.youtube.com/") ?: return@withContext InnertubeSearchPage()
            val root = JSONObject(resp)

            val songs = ArrayList<InnertubeSongItem>()
            val videos = ArrayList<InnertubeVideoItem>()
            val albums = ArrayList<InnertubeAlbumItem>()
            val artists = ArrayList<InnertubeArtistItem>()
            val playlists = ArrayList<InnertubePlaylistItem>()
            val seenKeys = HashSet<String>()
            var nextContinuation: String? = null

            fun parseNode(node: Any?) {
                when (node) {
                    is JSONObject -> {
                        // Continuation Token
                        if (node.has("continuationEndpoint")) {
                            val cont = node.getJSONObject("continuationEndpoint")
                                .optJSONObject("continuationCommand")?.optString("token")
                            if (!cont.isNullOrBlank()) nextContinuation = cont
                        }
                        if (node.has("nextContinuationData")) {
                            val cont = node.getJSONObject("nextContinuationData").optString("continuation")
                            if (cont.isNotBlank()) nextContinuation = cont
                        }

                        // Responsive List Item Renderer (Song, Video, Artist, Album, Playlist)
                        if (node.has("musicResponsiveListItemRenderer")) {
                            val r = node.getJSONObject("musicResponsiveListItemRenderer")
                            parseResponsiveItem(r, songs, videos, albums, artists, playlists, seenKeys)
                        }

                        // Two Row Item Renderer (Albums, Playlists, Artists grid cards)
                        if (node.has("musicTwoRowItemRenderer")) {
                            val r = node.getJSONObject("musicTwoRowItemRenderer")
                            parseTwoRowItem(r, albums, artists, playlists, seenKeys)
                        }

                        val keys = node.keys()
                        while (keys.hasNext()) parseNode(node.opt(keys.next()))
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) parseNode(node.opt(i))
                    }
                }
            }

            parseNode(root)

            Log.i(TAG, "Search '$query' (${filter.label}) parsed: ${songs.size} songs, ${videos.size} videos, ${albums.size} albums, ${artists.size} artists, hasMore=${nextContinuation != null}")

            InnertubeSearchPage(
                songs = songs,
                videos = videos,
                albums = albums,
                artists = artists,
                playlists = playlists,
                continuationToken = nextContinuation,
                hasMore = nextContinuation != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            InnertubeSearchPage()
        }
    }

    private fun parseResponsiveItem(
        r: JSONObject,
        songs: MutableList<InnertubeSongItem>,
        videos: MutableList<InnertubeVideoItem>,
        albums: MutableList<InnertubeAlbumItem>,
        artists: MutableList<InnertubeArtistItem>,
        playlists: MutableList<InnertubePlaylistItem>,
        seenKeys: MutableSet<String>
    ) {
        val vid = r.optJSONObject("playlistItemData")?.optString("videoId")
            ?: r.optJSONObject("doubleTapCommand")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?: ""

        val navEndpoint = r.optJSONObject("navigationEndpoint")
        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId").orEmpty()
        val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()

        val cols = r.optJSONArray("flexColumns") ?: return
        var title = ""
        var subtitle = ""
        var artist = ""
        var album = ""
        var albumBrowseId: String? = null
        var durationText: String? = null
        var durationMs = 0L

        if (cols.length() > 0) {
            val c0 = cols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            title = extractRunsText(c0)
        }
        if (cols.length() > 1) {
            val c1 = cols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val runs = c1?.optJSONObject("text")?.optJSONArray("runs")
            if (runs != null) {
                val parts = mutableListOf<String>()
                for (i in 0 until runs.length()) {
                    val run = runs.optJSONObject(i) ?: continue
                    val t = run.optString("text").trim()
                    if (t != "•" && t.isNotBlank()) {
                        if (t.matches(Regex("""\d+:\d+(:\d+)?"""))) {
                            durationText = t
                            durationMs = parseDuration(t)
                        } else if (t.lowercase() !in listOf("song", "video", "single", "album", "ep", "artist", "playlist")) {
                            parts.add(t)
                            val bId = run.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")?.optString("browseId")
                            if (!bId.isNullOrBlank() && bId.startsWith("MPRE")) {
                                albumBrowseId = bId
                            }
                        }
                    }
                }
                artist = parts.firstOrNull().orEmpty()
                album = parts.getOrNull(1).orEmpty()
                subtitle = parts.joinToString(" • ")
            }
        }

        // Thumbnail extraction: pick largest resolution
        val thumb = extractBestThumbnail(r.optJSONObject("thumbnail"))

        // Explicit badge check
        val badges = r.optJSONArray("badges")
        var isExplicit = false
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val b = badges.optJSONObject(i)?.optJSONObject("musicInlineBadgeRenderer")
                val label = b?.optString("accessibilityData") ?: b?.optString("icon")
                if (label?.contains("explicit", ignoreCase = true) == true) isExplicit = true
            }
        }

        // Canonical Routing
        when {
            vid.length == 11 && (pageType == "MUSIC_PAGE_TYPE_SONG" || pageType.isEmpty() || browseId.isEmpty()) -> {
                if (seenKeys.add("song_$vid") && title.isNotBlank()) {
                    songs.add(
                        InnertubeSongItem(
                            videoId = vid,
                            title = title,
                            artist = artist.ifBlank { "YouTube Music" },
                            album = album.ifBlank { "YouTube" },
                            albumBrowseId = albumBrowseId,
                            durationText = durationText,
                            durationMs = durationMs,
                            explicit = isExplicit,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            vid.length == 11 && pageType == "MUSIC_PAGE_TYPE_VIDEO" -> {
                if (seenKeys.add("video_$vid") && title.isNotBlank()) {
                    videos.add(
                        InnertubeVideoItem(
                            videoId = vid,
                            title = title,
                            author = artist.ifBlank { "YouTube" },
                            viewsText = subtitle,
                            durationText = durationText,
                            durationMs = durationMs,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            pageType == "MUSIC_PAGE_TYPE_ALBUM" && browseId.isNotBlank() -> {
                if (seenKeys.add("album_$browseId") && title.isNotBlank()) {
                    albums.add(
                        InnertubeAlbumItem(
                            browseId = browseId,
                            title = title,
                            artist = artist.ifBlank { "Album" },
                            artworkUrl = thumb
                        )
                    )
                }
            }
            pageType == "MUSIC_PAGE_TYPE_ARTIST" && browseId.isNotBlank() -> {
                if (seenKeys.add("artist_$browseId") && title.isNotBlank()) {
                    artists.add(
                        InnertubeArtistItem(
                            browseId = browseId,
                            name = title,
                            subscribersText = subtitle,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            (pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL")) && browseId.isNotBlank() -> {
                if (seenKeys.add("playlist_$browseId") && title.isNotBlank()) {
                    playlists.add(
                        InnertubePlaylistItem(
                            browseId = browseId,
                            title = title,
                            author = artist.ifBlank { "YouTube Playlist" },
                            artworkUrl = thumb
                        )
                    )
                }
            }
        }
    }

    private fun parseTwoRowItem(
        r: JSONObject,
        albums: MutableList<InnertubeAlbumItem>,
        artists: MutableList<InnertubeArtistItem>,
        playlists: MutableList<InnertubePlaylistItem>,
        seenKeys: MutableSet<String>
    ) {
        val nav = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return
        val browseId = nav.optString("browseId").orEmpty()
        val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()

        val title = extractRunsText(r.optJSONObject("title"))
        val subtitle = extractRunsText(r.optJSONObject("subtitle"))
        val thumb = extractBestThumbnail(r.optJSONObject("thumbnailRenderer"))

        when (pageType) {
            "MUSIC_PAGE_TYPE_ALBUM" -> {
                if (seenKeys.add("album_$browseId") && title.isNotBlank()) {
                    albums.add(
                        InnertubeAlbumItem(
                            browseId = browseId,
                            title = title,
                            artist = subtitle,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            "MUSIC_PAGE_TYPE_ARTIST" -> {
                if (seenKeys.add("artist_$browseId") && title.isNotBlank()) {
                    artists.add(
                        InnertubeArtistItem(
                            browseId = browseId,
                            name = title,
                            subscribersText = subtitle,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                if (seenKeys.add("playlist_$browseId") && title.isNotBlank()) {
                    playlists.add(
                        InnertubePlaylistItem(
                            browseId = browseId,
                            title = title,
                            author = subtitle,
                            artworkUrl = thumb
                        )
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // ARTIST PAGE BROWSER
    // -------------------------------------------------------------
    suspend fun getArtistPage(browseId: String): InnertubeArtistPage? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null
        try {
            val body = JSONObject()
                .put("context", makeContext("WEB_REMIX", "1.20241001.01.00"))
                .put("browseId", browseId)

            val url = "$MUSIC_BASE_URL/browse?prettyPrint=false"
            val resp = postJson(url, body.toString(), "https://music.youtube.com/") ?: return@withContext null
            val root = JSONObject(resp)

            val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")

            val artistName = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Artist"
            val desc = header?.optJSONObject("description")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            val artistThumb = extractBestThumbnail(header?.optJSONObject("thumbnail"))

            val topSongs = ArrayList<InnertubeSongItem>()
            val albums = ArrayList<InnertubeAlbumItem>()
            val singles = ArrayList<InnertubeAlbumItem>()
            val seenSongs = HashSet<String>()

            fun scanArtist(node: Any?) {
                when (node) {
                    is JSONObject -> {
                        if (node.has("musicResponsiveListItemRenderer")) {
                            val r = node.getJSONObject("musicResponsiveListItemRenderer")
                            val vid = r.optJSONObject("playlistItemData")?.optString("videoId")
                                ?: r.optJSONObject("doubleTapCommand")?.optJSONObject("watchEndpoint")?.optString("videoId")
                                ?: ""
                            val cols = r.optJSONArray("flexColumns")
                            if (vid.length == 11 && cols != null && cols.length() > 0 && seenSongs.add(vid)) {
                                val t = extractRunsText(cols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer"))
                                var durMs = 0L
                                var durText: String? = null
                                var albumTitle = "Singles"
                                if (cols.length() > 1) {
                                    val runs = cols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")?.optJSONArray("runs")
                                    if (runs != null) {
                                        for (i in 0 until runs.length()) {
                                            val txt = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
                                            if (txt.matches(Regex("""\d+:\d+"""))) {
                                                durText = txt
                                                durMs = parseDuration(txt)
                                            } else if (txt != "•" && txt != artistName && txt.isNotBlank()) {
                                                albumTitle = txt
                                            }
                                        }
                                    }
                                }
                                val songThumb = extractBestThumbnail(r.optJSONObject("thumbnail")) ?: artistThumb
                                topSongs.add(
                                    InnertubeSongItem(
                                        videoId = vid,
                                        title = t,
                                        artist = artistName,
                                        album = albumTitle,
                                        durationText = durText,
                                        durationMs = durMs,
                                        artworkUrl = songThumb
                                    )
                                )
                            }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) scanArtist(node.opt(keys.next()))
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) scanArtist(node.opt(i))
                    }
                }
            }

            scanArtist(root)

            InnertubeArtistPage(
                browseId = browseId,
                name = artistName,
                description = desc,
                artworkUrl = artistThumb,
                topSongs = topSongs,
                albums = albums,
                singles = singles
            )
        } catch (e: Exception) {
            Log.w(TAG, "getArtistPage error: ${e.message}")
            null
        }
    }

    // -------------------------------------------------------------
    // DIRECT AUTHORIZED AUDIO STREAM RESOLVER (CANONICAL VIDEO ID ONLY)
    // -------------------------------------------------------------
    /**
     * Resolves a direct, unthrottled audio stream URL for the EXACT videoId requested.
     * Uses client rotation (ANDROID_TESTSUITE, ANDROID_MUSIC, WEB_REMIX, IOS).
     *
     * STRICT INVARIANT:
     * Never searches for another song. If the requested videoId cannot be resolved
     * into a direct authorized stream, returns null so the UI can accurately indicate
     * playback unavailability rather than substituting an incorrect recording.
     */
    suspend fun resolveDirectAudioStream(videoId: String): DirectAudioStream? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null

        val clientContexts = listOf(
            Triple("ANDROID_TESTSUITE", "1.9", YOUTUBE_BASE_URL),
            Triple("ANDROID_MUSIC", "6.20.51", MUSIC_BASE_URL),
            Triple("WEB_REMIX", "1.20241001.01.00", MUSIC_BASE_URL),
            Triple("IOS", "19.29.1", YOUTUBE_BASE_URL)
        )

        for ((clientName, clientVersion, baseUrl) in clientContexts) {
            try {
                val body = JSONObject()
                    .put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)
                    .put("context", makeContext(clientName, clientVersion))

                val url = "$baseUrl/player?prettyPrint=false"
                val resp = postJson(url, body.toString(), "https://music.youtube.com/") ?: continue
                val root = JSONObject(resp)

                val playability = root.optJSONObject("playabilityStatus")
                val status = playability?.optString("status")
                if (status != "OK") {
                    Log.d(TAG, "resolveDirectAudioStream($videoId) on $clientName returned playability: $status")
                    continue
                }

                val streamingData = root.optJSONObject("streamingData") ?: continue
                val direct = pickDirectStream(videoId, streamingData)
                if (direct != null) {
                    Log.i(TAG, "RESOLVED direct stream for '$videoId' on $clientName (itag ${direct.itag}, exp in ${(direct.expiresAt - System.currentTimeMillis()) / 60000}m)")
                    return@withContext direct
                }
            } catch (e: Exception) {
                Log.d(TAG, "resolveDirectAudioStream attempt on $clientName failed: ${e.message}")
            }
        }

        Log.w(TAG, "resolveDirectAudioStream: No direct authorized stream available for exact videoId '$videoId'")
        null
    }

    private fun pickDirectStream(videoId: String, streamingData: JSONObject): DirectAudioStream? {
        val candidates = ArrayList<DirectAudioStream>()
        listOf("adaptiveFormats", "formats").forEach { key ->
            val arr = streamingData.optJSONArray(key) ?: return@forEach
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val directUrl = f.optString("url")
                if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                    val itag = f.optInt("itag", 0)
                    val mime = f.optString("mimeType")
                    val bitrate = f.optLong("bitrate", 0L)
                    val exp = extractExpireMillis(directUrl) ?: (System.currentTimeMillis() + 6 * 3600_000L)
                    if (exp > System.currentTimeMillis() + 60_000L) {
                        candidates.add(
                            DirectAudioStream(
                                videoId = videoId,
                                url = directUrl,
                                expiresAt = exp,
                                itag = itag,
                                mimeType = mime,
                                bitrate = bitrate
                            )
                        )
                    }
                }
            }
        }

        // Priority order: itag 140 (128kbps AAC/m4a), itag 251 (160kbps Opus), itag 18 (360p muxed mp4 audio)
        return candidates.firstOrNull { it.itag == 140 }
            ?: candidates.firstOrNull { it.itag == 251 }
            ?: candidates.firstOrNull { it.itag == 18 }
            ?: candidates.firstOrNull { it.mimeType.contains("audio", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    // -------------------------------------------------------------
    // HELPERS & NETWORK UTILITIES
    // -------------------------------------------------------------
    private fun makeContext(clientName: String, clientVersion: String): JSONObject {
        return JSONObject().put(
            "client", JSONObject()
                .put("clientName", clientName)
                .put("clientVersion", clientVersion)
                .put("hl", languageCode())
                .put("gl", countryCode())
        )
    }

    private fun extractRunsText(node: JSONObject?): String {
        val runs = node?.optJSONObject("text")?.optJSONArray("runs")
            ?: node?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            sb.append(r.optString("text"))
        }
        return sb.toString().trim()
    }

    private fun extractBestThumbnail(thumbNode: JSONObject?): String? {
        if (thumbNode == null) return null
        val arr = thumbNode.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: thumbNode.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: thumbNode.optJSONArray("thumbnails")
            ?: return null

        var bestUrl: String? = null
        var maxArea = -1
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val u = item.optString("url")
            val w = item.optInt("width", 0)
            val h = item.optInt("height", 0)
            val area = w * h
            if (u.isNotBlank() && (area >= maxArea || bestUrl == null)) {
                maxArea = area
                bestUrl = u
            }
        }
        return bestUrl
    }

    private fun parseDuration(d: String): Long {
        val parts = d.split(":")
        return when (parts.size) {
            2 -> (parts[0].toLongOrNull() ?: 0) * 60_000L + (parts[1].toLongOrNull() ?: 0) * 1000L
            3 -> (parts[0].toLongOrNull() ?: 0) * 3600_000L + (parts[1].toLongOrNull() ?: 0) * 60_000L + (parts[2].toLongOrNull() ?: 0) * 1000L
            else -> 0L
        }
    }

    private fun extractExpireMillis(url: String): Long? {
        val match = Regex("""[?&]expire=(\d+)""").find(url) ?: return null
        val sec = match.groupValues[1].toLongOrNull() ?: return null
        return sec * 1000L
    }

    private fun postJson(url: String, json: String, referer: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .header("Referer", referer)
            .header("Origin", referer.removeSuffix("/"))
            .header("X-Goog-Api-Key", API_KEY)
            .header("X-Goog-Api-Format-Version", "2")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }
}
