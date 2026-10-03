/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.source

import android.util.Log
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.youtube.YouTubeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated Playback Resolver enforcing strict canonical track identity.
 *
 * CANONICAL INVARIANTS:
 * 1. Resolves exclusively the exact track requested by the user.
 * 2. NEVER substitutes a different recording, cover, remix, karaoke, live, or alternate artist.
 * 3. Never plays 20-30s preview cuts.
 * 4. Binds authoritative metadata & artwork directly to the playback result.
 * 5. If the exact stream is unavailable, reports Unavailable rather than playing an unexpected audio track.
 */
class PlaybackResolver(
    private val youtube: YouTubeSource
) {
    companion object {
        private const val TAG = "PlaybackResolver"
    }

    private val verifiedStreams = ConcurrentHashMap<String, PlaybackResult.Success>()
    private val failedStreams = ConcurrentHashMap<String, MutableSet<String>>()

    fun markStreamFailed(trackId: String, streamUrl: String) {
        val set = failedStreams.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }
        set.add(streamUrl)
        youtube.markFailed(trackId, streamUrl)
        verifiedStreams.remove(trackId)
        Log.w(TAG, "markStreamFailed: track=$trackId url=${streamUrl.take(60)}")
    }

    fun clearFailures(trackId: String) {
        failedStreams.remove(trackId)
        youtube.clearFailures(trackId)
    }

    suspend fun resolve(track: TrackIdentity): PlaybackResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "REQUESTED: id=${track.id} title='${track.title}' artist='${track.artist}' provider=${track.sourceProvider} videoId=${track.sourceTrackId}")

        // 1. LOCAL TRACK: Stream local contentUri directly
        if (track.isLocal && track.contentUri.isNotBlank() && !track.contentUri.startsWith(YouTubeSource.VT)) {
            Log.i(TAG, "RESOLVED: Local track '${track.title}' -> ${track.contentUri}")
            return@withContext PlaybackResult.Success(
                streamUrl = track.contentUri,
                durationMs = track.durationMs,
                format = "local",
                expiresAt = Long.MAX_VALUE,
                provider = "local"
            )
        }

        // 2. CACHE HIT: Check unexpired verified stream for this EXACT track
        val exclusions = failedStreams[track.id].orEmpty()
        val cached = verifiedStreams[track.id]
        if (cached != null && cached.expiresAt > (System.currentTimeMillis() + 60_000L) && cached.streamUrl !in exclusions) {
            Log.i(TAG, "RESOLVED: Cache hit for exact track '${track.title}' (expires in ${(cached.expiresAt - System.currentTimeMillis()) / 60000}m)")
            return@withContext cached
        }

        try {
            // 3. RESOLVE EXACT PROVIDER VIDEO ID
            val songObj = track.toSong()
            val stream = youtube.resolve(songObj)

            if (stream != null && stream.url !in exclusions) {
                // Reject preview cuts
                if (stream.url.contains("p.scdn.co") || stream.url.contains("preview")) {
                    Log.w(TAG, "REJECTED preview cut for '${track.title}'")
                    return@withContext PlaybackResult.Unavailable("Full-length authorized audio stream unavailable (preview cut rejected).")
                }

                val success = PlaybackResult.Success(
                    streamUrl = stream.url,
                    durationMs = track.durationMs,
                    format = stream.source,
                    expiresAt = stream.expiresAt,
                    provider = stream.source
                )
                verifiedStreams[track.id] = success
                Log.i(TAG, "RESOLVED: Canonical '${track.title}' via ${stream.source} (exp=${(stream.expiresAt - System.currentTimeMillis()) / 60000}m)")
                return@withContext success
            }

            Log.e(TAG, "TRACK RESOLUTION FAILED for exact recording '${track.title}' ($track.id) — ${youtube.lastError}")
            PlaybackResult.Unavailable("The exact recording '${track.title}' is currently unavailable from YouTube Music.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving '${track.title}': ${e.message}", e)
            PlaybackResult.Unavailable("Playback resolution error: ${e.message?.take(80)}")
        }
    }
}
