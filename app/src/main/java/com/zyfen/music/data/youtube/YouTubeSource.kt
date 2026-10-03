/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.youtube

import android.util.Log
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.online.innertube.InnertubeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient, canonical YouTube Music audio stream resolver.
 *
 * CANONICAL IDENTITY GUARANTEE:
 * 1. Resolves ONLY the exact videoId specified in the requested Song / TrackIdentity.
 * 2. NEVER queries YouTube search or third-party fallback engines to substitute covers,
 *    remixes, live cuts, karaoke, or alternate recordings.
 * 3. If direct permitted streams for the exact videoId are unavailable, resolution returns null
 *    so PlaybackResolver can notify the user honestly without playing an unexpected track.
 */
class YouTubeSource(
    private val innertube: InnertubeClient = InnertubeClient()
) {

    data class Stream(
        val videoId: String,
        val url: String,
        val expiresAt: Long,
        val source: String = "youtube"
    )

    @Volatile
    var lastError: String = "Ready"
        private set

    private val scope = CoroutineScope(Dispatchers.IO)
    private val videoIds = ConcurrentHashMap<String, String>()
    private val streams = ConcurrentHashMap<String, Stream>()
    private val inflight = ConcurrentHashMap<String, Deferred<Stream?>>()
    private val failures = ConcurrentHashMap<String, MutableSet<String>>()

    fun markFailed(songId: String, url: String) {
        val set = failures.computeIfAbsent(songId) { ConcurrentHashMap.newKeySet() }
        set.add("url:$url")
        Log.w(TAG, "markFailed: song=$songId url=${url.take(50)}...")
    }

    fun clearFailures(songId: String) {
        failures.remove(songId)?.let { Log.i(TAG, "clearFailures: song=$songId") }
    }

    fun videoIdOf(song: Song): String? {
        videoIds[song.id]?.let { return it }

        if (song.contentUri.startsWith(VT)) {
            val vid = song.contentUri.removePrefix(VT)
            if (vid.length == 11) {
                videoIds[song.id] = vid
                return vid
            }
        }

        if (song.id.startsWith("yt_")) {
            val vid = song.id.removePrefix("yt_")
            if (vid.length == 11) {
                videoIds[song.id] = vid
                return vid
            }
        }

        return null
    }

    suspend fun resolve(song: Song): Stream? = withContext(Dispatchers.IO) {
        val excl = failures[song.id].orEmpty()

        // 1. Instant Cache hit check by song.id (< 0.001s)
        streams[song.id]?.takeIf {
            it.expiresAt > (System.currentTimeMillis() + 60_000L) && "url:${it.url}" !in excl
        }?.let {
            Log.i(TAG, "resolve: instant songId cache hit '${song.title}'")
            return@withContext it
        }

        // 2. Cache hit check by canonical videoId
        val vid = videoIdOf(song)
        if (vid != null) {
            streams[vid]?.takeIf {
                it.expiresAt > (System.currentTimeMillis() + 60_000L) && "url:${it.url}" !in excl
            }?.let {
                Log.i(TAG, "resolve: cache hit vid=$vid '${song.title}'")
                return@withContext it
            }
        }

        val key = song.id
        inflight[key]?.let { return@withContext it.await() }

        val job = scope.async { resolveInternal(song, vid) }
        inflight[key] = job
        try {
            job.await()
        } finally {
            inflight.remove(key)
        }
    }

    fun prefetchSong(song: Song) {
        if (streams.containsKey(song.id)) return
        scope.launch {
            runCatching { resolve(song) }
        }
    }

    private suspend fun resolveInternal(song: Song, videoId: String?): Stream? = coroutineScope {
        lastError = "Resolving audio stream…"
        val excl = failures[song.id].orEmpty()

        // Direct content URI already provided (and not a preview cut)
        if (song.contentUri.startsWith("http") &&
            !song.contentUri.contains("preview") &&
            !song.contentUri.contains("p.scdn.co") &&
            "url:${song.contentUri}" !in excl
        ) {
            return@coroutineScope store(song, videoId ?: song.id, song.contentUri, "direct")
        }

        // Must have a valid canonical videoId
        val targetVideoId = videoId ?: videoIdOf(song)
        if (targetVideoId == null || targetVideoId.length != 11) {
            lastError = "Track lacks canonical YouTube videoId: '${song.title}'"
            Log.w(TAG, lastError)
            return@coroutineScope null
        }

        // Resolve stream directly for targetVideoId
        val directStream = innertube.resolveDirectAudioStream(targetVideoId)
        if (directStream != null && "url:${directStream.url}" !in excl) {
            return@coroutineScope store(song, targetVideoId, directStream.url, "youtube")
        }

        lastError = "Direct audio stream unavailable for exact videoId '$targetVideoId'"
        Log.e(TAG, "resolve FAILED for '${song.title}' ($targetVideoId) — $lastError")
        null
    }

    private fun store(song: Song, vid: String, url: String, source: String): Stream {
        videoIds[song.id] = vid
        val exp = extractExpireMillis(url) ?: (System.currentTimeMillis() + 6 * 3600_000L)
        val s = Stream(vid, url, exp, source)
        streams[song.id] = s
        streams[vid] = s
        lastError = "OK"
        Log.i(TAG, "store: OK vid=$vid song='${song.title}' exp in ${(exp - System.currentTimeMillis()) / 60000}m")
        return s
    }

    private fun extractExpireMillis(url: String): Long? {
        val match = Regex("""[?&]expire=(\d+)""").find(url) ?: return null
        val sec = match.groupValues[1].toLongOrNull() ?: return null
        return sec * 1000L
    }

    companion object {
        const val VT = "yt:"
        private const val TAG = "YouTubeSource"
    }
}
