/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.source

import android.util.LruCache

/**
 * Thread-safe, provider-qualified artwork cache.
 * Keyed strictly by `provider:trackId` to guarantee 100% artwork parity with the selected track.
 */
object ArtworkCache {

    private val cache = LruCache<String, String>(1000)

    fun get(identity: TrackIdentity): String? {
        val key = identity.cacheKey
        return cache.get(key) ?: identity.artworkUrl
    }

    fun get(provider: String, trackId: String): String? {
        if (trackId.isBlank()) return null
        return cache.get("${provider.lowercase()}:$trackId")
    }

    fun put(identity: TrackIdentity, url: String) {
        if (url.isNotBlank()) {
            cache.put(identity.cacheKey, url)
        }
    }

    fun put(provider: String, trackId: String, url: String) {
        if (url.isNotBlank() && trackId.isNotBlank()) {
            cache.put("${provider.lowercase()}:$trackId", url)
        }
    }

    fun remove(identity: TrackIdentity) {
        cache.remove(identity.cacheKey)
    }

    fun clear() {
        cache.evictAll()
    }
}
