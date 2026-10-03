package com.zyfen.music.data.spotify

import android.util.Log
import com.zyfen.music.data.local.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.HttpException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
data class ImportedSpotifyPlaylist(
    val name: String,
    val artworkUrl: String?,
    val spotifyUrl: String?,
    val tracks: List<SpotifyTrack>,
    val total: Int = tracks.size,
    val skipped: Int = 0,
    val truncated: Boolean = false
)

class SpotifyRepository(
    private val api: SpotifyService,
    private val session: SpotifySession,
    private val client: OkHttpClient,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {
    companion object {
        private const val TAG = "ZyfenImport"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        // Safety ceiling only (10_000 pages = 1M tracks) — NOT a track limit.
        // Spotify always moves `next` forward, so this never triggers in practice.
        private const val SANITY_MAX_PAGES = 10_000
        private const val BATCH = SpotifyService.PAGE_SIZE
    }

    suspend fun importPlaylist(
        linkOrId: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportedSpotifyPlaylist = withContext(Dispatchers.IO) {
        val pid = SpotifyLinkParser.extractPlaylistId(linkOrId)
            ?: throw IllegalArgumentException("That doesn't look like a Spotify playlist link.")

        // 1) Fast embed fetch (~300ms) - gives real title, cover art, and up to 100 tracks
        val embedDeferred = async { fetchViaEmbed(pid) }
        // 2) Fast spclient fetch (~200ms) - gives full list of all 300+ track IDs
        val spDeferred = async { fetchSpclientTrackIds(pid) }

        val embed = embedDeferred.await()
        val spData = spDeferred.await()

        val plName = embed?.name?.takeIf { it.isNotBlank() }
            ?: spData?.name?.takeIf { it.isNotBlank() }
            ?: "Spotify Playlist"
        val artUrl = embed?.artworkUrl ?: spData?.artworkUrl

        val spIds = spData?.ids.orEmpty()
        val embedTracks = embed?.tracks.orEmpty()
        val embedById = embedTracks.associateBy { it.id }

        // If spclient returned full track IDs (e.g. 309 tracks)
        if (spIds.isNotEmpty()) {
            val total = spIds.size
            onProgress(embedTracks.size.coerceAtMost(total), total)

            // Assemble all tracks immediately (instant - zero delay)
            val allTracks = ArrayList<SpotifyTrack>(total)
            val remainingIds = ArrayList<Pair<Int, String>>()

            for ((idx, id) in spIds.withIndex()) {
                val existing = embedById[id]
                if (existing != null) {
                    allTracks.add(existing)
                } else {
                    remainingIds.add(idx to id)
                    allTracks.add(
                        SpotifyTrack(
                            id = id,
                            name = "Track ${idx + 1}",
                            artists = emptyList(), // Never set playlist name as artist
                            album = SpotifyAlbum(
                                name = plName,
                                images = listOfNotNull(embed?.artworkUrl?.let { SpotifyImage(it) })
                            ),
                            durationMs = 0,
                            externalUrls = SpotifyExternalUrls("https://open.spotify.com/track/$id"),
                            previewUrl = null
                        )
                    )
                }
            }

            // Quick parallel enrichment for immediate tracks
            if (remainingIds.isNotEmpty()) {
                runCatching {
                    withTimeoutOrNull(2500L) {
                        val sem = Semaphore(15)
                        remainingIds.take(80).map { (idx, id) ->
                            async {
                                sem.withPermit {
                                    val info = fetchFastTrackInfo(id)
                                    if (info != null) {
                                        val old = allTracks[idx]
                                        allTracks[idx] = old.copy(
                                            name = info.title.ifBlank { old.name },
                                            artists = if (!info.artist.isNullOrBlank()) listOf(SpotifyArtist(info.artist)) else old.artists,
                                            album = if (!info.thumbnail.isNullOrBlank()) {
                                                SpotifyAlbum(name = old.album?.name ?: "", images = listOf(SpotifyImage(info.thumbnail)))
                                            } else old.album
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            }

            onProgress(total, total)

            val importedPlaylist = ImportedSpotifyPlaylist(
                name = plName,
                artworkUrl = artUrl,
                spotifyUrl = "https://open.spotify.com/playlist/$pid",
                tracks = allTracks,
                total = total,
                skipped = 0,
                truncated = false
            )

            // Persist all tracks to Room database
            persistIncremental(importedPlaylist, pid, onProgress)

            // High-speed parallel background worker to enrich remaining titles & artwork
            if (remainingIds.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val sem = Semaphore(10)
                        remainingIds.map { (idx, id) ->
                            async {
                                sem.withPermit {
                                    val track = allTracks.getOrNull(idx)
                                    if (track == null || track.name.startsWith("$plName #") || track.album?.images.isNullOrEmpty() || track.artists.isEmpty()) {
                                        val resolved = fetchFastTrackInfo(id)
                                        if (resolved != null) {
                                            val sid = "spotify_$id"
                                            val existingSong = songDao.byId(sid)
                                            if (existingSong != null) {
                                                songDao.upsert(
                                                    existingSong.copy(
                                                        title = resolved.title.ifBlank { existingSong.title },
                                                        artist = resolved.artist?.ifBlank { null } ?: existingSong.artist,
                                                        artworkUri = resolved.thumbnail?.ifBlank { null } ?: existingSong.artworkUri
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            }

            return@withContext importedPlaylist
        }

        // Fallback: Embed-only if spclient was unavailable
        if (embed != null && embed.tracks.isNotEmpty()) {
            onProgress(embed.tracks.size, embed.tracks.size)
            persistIncremental(embed, pid, onProgress)
            return@withContext embed
        }

        throw IllegalStateException("Could not load Spotify playlist. Please check your internet or the link.")
    }

    private data class SpclientResult(
        val name: String,
        val artworkUrl: String?,
        val ids: List<String>
    )

    private suspend fun fetchSpclientTrackIds(pid: String): SpclientResult? = withContext(Dispatchers.IO) {
        try {
            session.ensure()
        } catch (e: Exception) {
            Log.w(TAG, "spclient session failed: ${e.message}")
            return@withContext null
        }

        var total = -1
        var plName = ""
        var artUrl: String? = null
        val ids = ArrayList<String>()
        var offset = 0
        var pages = 0

        while (pages < SANITY_MAX_PAGES) {
            val body = spGet(
                "https://spclient.wg.spotify.com/playlist/v2/playlist/$pid?length=500&offset=$offset&markets=IN"
            ) ?: break
            val json = try { JSONObject(body) } catch (e: Exception) { break }
            if (total < 0) {
                total = json.optInt("length", -1)
                val attrs = json.optJSONObject("attributes")
                plName = attrs?.optString("name").orEmpty()
                artUrl = attrs?.optString("picture").orEmpty()
                    .takeIf { it.isNotBlank() }?.let { decodePictureGid(it) }
            }
            val items = json.optJSONObject("contents")?.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                if (total in 1..ids.size) break
                val uri = items.getJSONObject(i).optString("uri")
                if (uri.startsWith("spotify:track:")) ids.add(uri.substringAfterLast(':'))
            }
            offset += items.length()
            pages++
            if (total > 0 && offset >= total) break
        }

        if (ids.isEmpty()) return@withContext null
        SpclientResult(name = plName, artworkUrl = artUrl, ids = ids)
    }

    private data class FastTrackInfo(
        val title: String,
        val artist: String?,
        val thumbnail: String?,
        val durationMs: Long = 0L
    )

    private suspend fun fetchFastTrackInfo(trackId: String): FastTrackInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://open.spotify.com/embed/track/$trackId")
                .header("User-Agent", UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body!!.string()
                    val match = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""").find(html)
                    if (match != null) {
                        val root = JSONObject(match.groupValues[1])
                        val entity = root.optJSONObject("props")
                            ?.optJSONObject("pageProps")
                            ?.optJSONObject("state")
                            ?.optJSONObject("data")
                            ?.optJSONObject("entity")
                        if (entity != null) {
                            val title = entity.optString("name")
                            val artistArr = entity.optJSONArray("artists")
                            val artist = (0 until (artistArr?.length() ?: 0)).mapNotNull {
                                artistArr?.getJSONObject(it)?.optString("name")
                            }.joinToString(", ")
                            val imgArr = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
                            val visual = if (imgArr != null && imgArr.length() > 0) {
                                imgArr.optJSONObject(imgArr.length() - 1)?.optString("url")
                                    ?: imgArr.optJSONObject(0)?.optString("url")
                            } else null
                            val dur = entity.optLong("duration", 0L)
                            if (title.isNotBlank()) {
                                val meta = if (visual.isNullOrBlank() || artist.isBlank()) {
                                    com.zyfen.music.data.online.TrackMetadataResolver.resolve(title, artist)
                                } else null

                                return@withContext FastTrackInfo(
                                    title = title,
                                    artist = artist.ifBlank { meta?.artist },
                                    thumbnail = visual?.ifBlank { null } ?: meta?.artworkUrl,
                                    durationMs = dur
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: Spotify oEmbed
        try {
            val oReq = Request.Builder()
                .url("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$trackId")
                .header("User-Agent", UA)
                .build()
            client.newCall(oReq).execute().use { oResp ->
                if (oResp.isSuccessful) {
                    val oJson = JSONObject(oResp.body!!.string())
                    val oTitle = oJson.optString("title")
                    val oThumb = oJson.optString("thumbnail_url").ifBlank { null }
                    if (oTitle.isNotBlank()) {
                        val meta = com.zyfen.music.data.online.TrackMetadataResolver.resolve(oTitle, null)
                        return@withContext FastTrackInfo(
                            title = meta?.title ?: oTitle,
                            artist = meta?.artist,
                            thumbnail = meta?.artworkUrl ?: oThumb,
                            durationMs = 0L
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    private val enrichClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fills per-track artwork (unique thumbnail per song) using high-resolution
     * artwork providers (iTunes 600x600 & Spotify CDN) so every track has its real cover.
     */
    private suspend fun enrichMissingArtwork(p: ImportedSpotifyPlaylist): ImportedSpotifyPlaylist =
        withContext(Dispatchers.IO) {
            val need = p.tracks.filter { it.album?.images.isNullOrEmpty() }
            if (need.isEmpty()) return@withContext p

            val sem = Semaphore(10)
            val results = need.map { t ->
                async {
                    sem.withPermit {
                        fetchArtworkByTitleArtist(t.name, t.artists.firstOrNull()?.name)
                    }
                }
            }.awaitAll()

            val byId = need.mapIndexedNotNull { i, t ->
                results[i]?.let { (t.id ?: "$i") to it }
            }.toMap()
            if (byId.isEmpty()) return@withContext p

            p.copy(tracks = p.tracks.mapIndexed { idx, t ->
                val key = t.id ?: "$idx"
                val url = byId[key]
                if (url == null) t else t.copy(
                    album = SpotifyAlbum(
                        name = t.album?.name ?: "",
                        images = listOf(SpotifyImage(url))
                    )
                )
            })
        }

    private suspend fun fetchArtworkByTitleArtist(title: String, artist: String?): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val query = "$title ${artist ?: ""}".trim()
            val enc = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$enc&entity=song&limit=1")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            enrichClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val root = JSONObject(r.body!!.string())
                val results = root.optJSONArray("results") ?: return@withContext null
                if (results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val raw = item.optString("artworkUrl100")
                    if (raw.isNotBlank()) {
                        raw.replace("100x100bb", "600x600bb")
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- source 0: spclient playlist v2 + per-track embed metadata ----------

    /**
     * Full playlist via spclient.wg.spotify.com/playlist/v2 (needs only the anonymous
     * access token — no client-token, which some networks reject). Returns every track
     * URI in one paginated sweep (length=500/page, no cap), then fetches each track's
     * metadata (title / artists / duration / artwork) from its public embed page
     * (__NEXT_DATA__), 6 at a time, with live progress. Duplicates are dropped from
     * the result and counted as skipped, matching Spotify's own list (unique tracks).
     */
    private suspend fun tryImportViaSpclient(
        pid: String,
        onProgress: (Int, Int) -> Unit
    ): ImportedSpotifyPlaylist? = withContext(Dispatchers.IO) {
        try {
            session.ensure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "spclient: session failed: ${e.message}")
            return@withContext null
        }

        // 1) full URI list (paginated; page size 500 — server may return fewer)
        var total = -1
        var plName = ""
        var artUrl: String? = null
        val ids = ArrayList<String>()
        var offset = 0
        var pages = 0
        while (pages < SANITY_MAX_PAGES) {
            val body = spGet(
                "https://spclient.wg.spotify.com/playlist/v2/playlist/$pid" +
                    "?length=500&offset=$offset&markets=IN"
            ) ?: break
            val json = try { JSONObject(body) } catch (e: Exception) { break }
            if (total < 0) {
                total = json.optInt("length", -1)
                val attrs = json.optJSONObject("attributes")
                plName = attrs?.optString("name").orEmpty()
                artUrl = attrs?.optString("picture").orEmpty()
                    .takeIf { it.isNotBlank() }?.let { decodePictureGid(it) }
            }
            val items = json.optJSONObject("contents")?.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                if (total in 1..ids.size) break
                val uri = items.getJSONObject(i).optString("uri")
                if (uri.startsWith("spotify:track:")) ids.add(uri.substringAfterLast(':'))
            }
            offset += items.length()
            pages++
            if (total > 0 && offset >= total) break
        }
        if (ids.isEmpty()) {
            Log.w(TAG, "spclient: no track URIs (total=$total pages=$pages)")
            return@withContext null
        }

        val unique = ids.distinct()
        val dupSkips = ids.size - unique.size
        Log.i(
            TAG,
            "spclient list ok: name='$plName' total=$total uris=${ids.size} " +
                "unique=${unique.size} pages=$pages"
        )

        // 2) metadata resolution: batch query official API in chunks of 50 (fast, never rate-limited!)
        val done = AtomicInteger(0)
        onProgress(0, unique.size)
        val tracks = ArrayList<SpotifyTrack>(unique.size)
        val chunks = unique.chunked(50)
        for (chunk in chunks) {
            val batch = runCatching {
                retry(times = 3) {
                    api.getTracks(chunk.joinToString(","))
                }
            }.getOrNull()

            val batchTracks = batch?.tracks?.filterNotNull().orEmpty()
            if (batchTracks.isNotEmpty()) {
                val byId = batchTracks.associateBy { it.id }
                for (id in chunk) {
                    val resolved = byId[id] ?: fetchEmbedTrack(id) ?: fallbackTrack(id, plName, artUrl)
                    tracks.add(resolved)
                    onProgress(done.incrementAndGet(), unique.size)
                }
            } else {
                for (id in chunk) {
                    val resolved = fetchEmbedTrack(id) ?: fallbackTrack(id, plName, artUrl)
                    tracks.add(resolved)
                    onProgress(done.incrementAndGet(), unique.size)
                }
            }
        }

        if (tracks.isEmpty()) {
            Log.w(TAG, "spclient: all ${unique.size} track fetches failed")
            return@withContext null
        }
        Log.i(
            TAG,
            "spclient import done: total=$total imported=${tracks.size} skipped=0"
        )
        ImportedSpotifyPlaylist(
            name = plName.ifBlank { "Spotify playlist" },
            artworkUrl = artUrl,
            spotifyUrl = "https://open.spotify.com/playlist/$pid",
            tracks = tracks,
            total = total.takeIf { it > 0 } ?: tracks.size,
            skipped = 0,
            truncated = false
        )
    }

    private fun fallbackTrack(id: String, plName: String, artUrl: String?): SpotifyTrack {
        return SpotifyTrack(
            id = id,
            name = "Track ${id.take(6)}",
            artists = listOf(SpotifyArtist(plName.ifBlank { "Spotify Track" })),
            album = SpotifyAlbum(name = plName, images = artUrl?.let { listOf(SpotifyImage(it)) } ?: emptyList()),
            durationMs = 0,
            externalUrls = SpotifyExternalUrls("https://open.spotify.com/track/$id"),
            previewUrl = null
        )
    }

    /** GET with retry; returns body or null (never throws for HTTP errors). */
    private suspend fun spGet(url: String): String? {
        repeat(3) { attempt ->
            try {
                val auth = session.authHeader()
                if (auth == null) {
                    Log.w(TAG, "spclient: no access token")
                    return null
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", auth)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .header("spotify-app-platform", "WebPlayer")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) return r.body?.string()
                    Log.w(TAG, "spclient HTTP ${r.code} (attempt ${attempt + 1})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "spclient failed (attempt ${attempt + 1}): ${e.message}")
            }
            if (attempt < 2) delay(1200L * (attempt + 1))
        }
        return null
    }

    /** Metadata for one track from its public embed page; null after 2 tries. */
    private suspend fun fetchEmbedTrack(id: String): SpotifyTrack? =
        withContext(Dispatchers.IO) {
            var lastErr: String? = null
            repeat(2) { attempt ->
                try {
                    val req = Request.Builder()
                        .url("https://open.spotify.com/embed/track/$id")
                        .header("User-Agent", UA)
                        .build()
                    val html = enrichClient.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) {
                            lastErr = "HTTP ${r.code}"
                            null
                        } else {
                            r.body?.string()
                        }
                    }
                    if (html != null) {
                        val parsed = parseEmbedTrack(id, html)
                        if (parsed != null) return@withContext parsed
                        lastErr = "no __NEXT_DATA__"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastErr = e.message
                }
                if (attempt == 0) delay(300L)
            }
            Log.w(TAG, "embed track fetch failed id=$id: $lastErr")
            null
        }

    private fun parseEmbedTrack(id: String, html: String): SpotifyTrack? {
        val m = Regex(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html) ?: return null
        val ent = runCatching {
            JSONObject(m.groupValues[1]).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("state").getJSONObject("data")
                .getJSONObject("entity")
        }.getOrNull() ?: return null
        val title = ent.optString("name").ifBlank { ent.optString("title") }
        if (title.isBlank()) return null
        val artists = ArrayList<SpotifyArtist>()
        ent.optJSONArray("artists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i).optString("name")
                if (n.isNotBlank()) artists.add(SpotifyArtist(n))
            }
        }
        if (artists.isEmpty()) artists.add(SpotifyArtist("Unknown Artist"))
        val artUrl = runCatching {
            val imgs = ent.getJSONObject("visualIdentity").getJSONArray("image")
            var best: String? = null
            var bestW = -1
            for (i in 0 until imgs.length()) {
                val o = imgs.getJSONObject(i)
                val w = o.optInt("maxWidth", 0)
                if (w >= bestW) {
                    bestW = w
                    best = o.optString("url")
                }
            }
            best?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val preview = ent.optJSONObject("audioPreview")?.optString("url")
            ?: ent.optString("preview_url").ifBlank { null }
        return SpotifyTrack(
            id = id,
            name = title,
            artists = artists,
            album = artUrl?.let { SpotifyAlbum(name = "", images = listOf(SpotifyImage(it))) },
            durationMs = ent.optLong("duration"),
            externalUrls = SpotifyExternalUrls("https://open.spotify.com/track/$id"),
            previewUrl = preview
        )
    }

    /** Playlist cover: base64 GID -> hex -> i.scdn.co/image/{hex}. */
    private fun decodePictureGid(gid: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(gid)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        "https://i.scdn.co/image/$hex"
    }.getOrNull()

    // ---------- source 1: official Web API with full pagination ----------

    private suspend fun tryImportViaApi(
        pid: String,
        onProgress: (Int, Int) -> Unit
    ): ImportedSpotifyPlaylist? {
        try {
            session.ensure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "session failed, will use embed fallback: ${e.message}")
            return null
        }

        val first = retry { api.getPlaylist(pid) }
        if (first == null) {
            Log.w(TAG, "playlist page 1 failed after retries — embed fallback")
            return null
        }

        val playlistId = "spotify_$pid"
        playlistDao.upsert(
            PlaylistEntity(
                id = playlistId,
                name = first.name,
                artworkUrl = first.images.firstOrNull()?.url,
                isSpotifyImport = true,
                spotifyId = pid,
                spotifyUrl = first.externalUrls?.spotify
            )
        )
        playlistDao.clearSongs(playlistId) // re-import refreshes; no stale/duplicate entries

        val total = first.tracks.total
        val all = ArrayList<SpotifyTrack>(total.coerceIn(0, 4096))
        val seen = HashSet<String>()
        var processed = 0
        var imported = 0
        var skipped = 0
        var position = 0

        suspend fun ingest(items: List<SpotifyPlaylistTrackItem>) {
            if (items.isEmpty()) return
            val songBatch = ArrayList<SongEntity>(items.size)
            val refBatch = ArrayList<PlaylistSongCrossRef>(items.size)
            for (item in items) {
                processed++
                val t = item.track
                if (t == null || t.name.isBlank()) { // removed / unavailable / invalid
                    skipped++
                    continue
                }
                val sid = if (t.id != null) "spotify_${t.id}" else "spotify_loc_$position"
                val entity = SongEntity(
                    id = sid,
                    title = t.name,
                    artist = t.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
                    album = t.album?.name ?: "",
                    durationMs = t.durationMs,
                    uri = "",
                    artworkUri = t.album?.images?.firstOrNull()?.url,
                    isLocal = false,
                    spotifyId = t.id,
                    spotifyUrl = t.externalUrls?.spotify
                )
                songBatch += entity
                if (seen.add(sid)) refBatch += PlaylistSongCrossRef(playlistId, sid, position)
                all += t
                imported++
                position++
            }
            // incremental batch insert — page by page, never the whole list at once
            songDao.upsertAll(songBatch)
            if (refBatch.isNotEmpty()) playlistDao.addSongs(refBatch)
            onProgress(processed, total)
        }

        ingest(first.tracks.items)
        Log.i(TAG, "import '${first.name}': page 1 ok, total=$total processed=$processed")

        var next = first.tracks.next
        var pages = 1
        while ((next != null || processed < total) && pages < SANITY_MAX_PAGES) {
            val url = next ?: "https://api.spotify.com/v1/playlists/$pid/tracks?offset=$processed&limit=100"
            val page = retry(times = 8) { api.getPlaylistPage(url) }
            if (page == null) {
                // If the next link failed, try constructing direct offset url
                val offsetUrl = "https://api.spotify.com/v1/playlists/$pid/tracks?offset=$processed&limit=100"
                val fallbackPage = retry(times = 8) { api.getPlaylistPage(offsetUrl) }
                if (fallbackPage == null) {
                    val lost = (total - processed).coerceAtLeast(0)
                    skipped += lost
                    Log.w(TAG, "page ${pages + 1} offset=$processed failed after 8 retries — keeping $imported tracks")
                    break
                }
                ingest(fallbackPage.items)
                next = fallbackPage.next
            } else {
                ingest(page.items)
                next = page.next
            }
            pages++
        }
        if (next == null && processed < total) skipped += total - processed

        Log.i(TAG, "import done: total=$total imported=$imported skipped=$skipped pages=$pages")
        return ImportedSpotifyPlaylist(
            name = first.name,
            artworkUrl = first.images.firstOrNull()?.url,
            spotifyUrl = first.externalUrls?.spotify,
            tracks = all,
            total = total,
            skipped = skipped,
            truncated = false
        )
    }

    /** Retries retryable failures (429 / 401 / 5xx / timeouts) with backoff; gives up -> null. */
    private suspend fun <T> retry(times: Int = 8, block: suspend () -> T): T? {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val httpEx = e as? HttpException
                val code = httpEx?.code()
                val retryable = code == null || code == 429 || code == 401 || code >= 500
                Log.w(TAG, "attempt $attempt failed${code?.let { " (HTTP $it)" } ?: ""}: ${e.message}")
                if (code == 401) {
                    session.invalidate()
                    runCatching { session.ensure(force = true) }
                    delay(400)
                } else if (code == 429) {
                    val retryAfter = httpEx.response()?.headers()?.get("Retry-After")?.toLongOrNull() ?: (2L * attempt)
                    delay((retryAfter + 1).coerceAtMost(10L) * 1000L)
                } else {
                    delay(1000L * attempt)
                }
                if (!retryable || attempt >= times) return null
            }
        }
    }

    // ---------- source 2: Spotify embed page (fallback, ~100 tracks) ----------

    private suspend fun fetchViaEmbed(pid: String): ImportedSpotifyPlaylist? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$pid")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    )
                    .build()
                val html = client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    r.body?.string() ?: return@withContext null
                }
                val m = Regex(
                    "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
                    RegexOption.DOT_MATCHES_ALL
                ).find(html) ?: return@withContext null
                val root = JSONObject(m.groupValues[1])
                val entity = root.getJSONObject("props").getJSONObject("pageProps")
                    .getJSONObject("state").getJSONObject("data")
                    .getJSONObject("entity")

                val name = entity.optString("name").ifBlank { entity.optString("title") }
                if (name.isBlank()) return@withContext null

                val art = runCatching {
                    entity.getJSONObject("coverArt").getJSONArray("sources")
                        .getJSONObject(0).getString("url")
                }.getOrNull()

                val openUrl = "https://open.spotify.com/playlist/$pid"

                val arr = entity.optJSONArray("trackList") ?: return@withContext null
                val tracks = (0 until arr.length()).mapNotNull { i ->
                    val t = arr.getJSONObject(i)
                    if (t.optBoolean("isPlayable") == false) return@mapNotNull null
                    val trackUri = t.optString("uri")
                    val trackId = trackUri.substringAfterLast(":").ifBlank { null }
                        ?: return@mapNotNull null
                    val openTrack = if (trackUri.startsWith("spotify:track:"))
                        "https://open.spotify.com/track/$trackId" else null
                    val audioPrev = t.optJSONObject("audioPreview")?.optString("url")
                        ?: t.optString("preview_url").ifBlank { null }
                    val trackArt = t.optJSONObject("thumbnail")?.optString("url")
                        ?: t.optString("artworkUrl").ifBlank { null }
                        ?: art
                    SpotifyTrack(
                        id = trackId,
                        name = t.optString("title"),
                        artists = listOf(SpotifyArtist(t.optString("subtitle").ifBlank { "Unknown Artist" })),
                        album = SpotifyAlbum(
                            name = "",
                            images = listOfNotNull(trackArt?.let { SpotifyImage(it) })
                        ),
                        durationMs = t.optLong("duration"),
                        externalUrls = SpotifyExternalUrls(openTrack),
                        previewUrl = audioPrev
                    )
                }
                if (tracks.isEmpty()) null else ImportedSpotifyPlaylist(
                    name = name,
                    artworkUrl = art,
                    spotifyUrl = openUrl.ifBlank { null },
                    tracks = tracks,
                    total = tracks.size,
                    skipped = 0,
                    truncated = tracks.size >= 100
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "embed fetch failed: ${e.message}")
                null
            }
        }

    // ---------- page-by-page persistence (shared by embed path) ----------

    private suspend fun persistIncremental(
        p: ImportedSpotifyPlaylist,
        pid: String,
        onProgress: (Int, Int) -> Unit
    ) {
        val playlistId = "spotify_$pid"
        playlistDao.upsert(
            PlaylistEntity(
                id = playlistId,
                name = p.name,
                artworkUrl = p.artworkUrl,
                isSpotifyImport = true,
                spotifyId = pid,
                spotifyUrl = p.spotifyUrl
            )
        )
        playlistDao.clearSongs(playlistId)

        val total = p.tracks.size
        var processed = 0
        var position = 0
        val seen = HashSet<String>()
        for (chunk in p.tracks.chunked(BATCH)) {
            val songBatch = ArrayList<SongEntity>(chunk.size)
            val refBatch = ArrayList<PlaylistSongCrossRef>(chunk.size)
            for (t in chunk) {
                if (t.name.isBlank()) { processed++; continue }
                val rawSid = if (t.id != null) "spotify_${t.id}" else "spotify_loc_$position"
                val sid = if (seen.add(rawSid)) rawSid else "${rawSid}_p$position"
                songBatch += SongEntity(
                    id = sid,
                    title = t.name,
                    artist = t.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
                    album = t.album?.name ?: "",
                    durationMs = t.durationMs,
                    uri = "",
                    artworkUri = t.album?.images?.firstOrNull()?.url,
                    isLocal = false,
                    spotifyId = t.id,
                    spotifyUrl = t.externalUrls?.spotify
                )
                refBatch += PlaylistSongCrossRef(playlistId, sid, position)
                processed++
                position++
            }
            songDao.upsertAll(songBatch)
            if (refBatch.isNotEmpty()) playlistDao.addSongs(refBatch)
            onProgress(processed, total)
        }
        Log.i(TAG, "embed import done: total=$total processed=$processed")
    }
}
