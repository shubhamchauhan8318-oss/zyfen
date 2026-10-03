/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.online

import android.util.LruCache

data class ResolvedTrackMeta(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val album: String? = null
)

/**
 * Metadata and artwork formatting utility.
 * Preserves the canonical identity established by the provider.
 * Does NOT overwrite YouTube Music artist or title metadata with external searches.
 */
object TrackMetadataResolver {

    private val artworkCache = LruCache<String, String>(1000)

    fun cleanArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return ""
        val trimmed = rawArtist.trim()
        val lower = trimmed.lowercase()
        if (lower.contains("zyfen") || lower.contains("spotify") || lower.contains("playlist") ||
            lower == "unknown artist" || lower == "various artists" || lower.startsWith("sp_")
        ) {
            return ""
        }
        return trimmed
    }

    fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""\s*#\d+\b"""), "")
            .replace(Regex("""(?i)\s*\[official.*?]|\(official.*?\)"""), "")
            .trim()
    }

    suspend fun resolve(title: String, rawArtist: String?): ResolvedTrackMeta? {
        val sanitizedTitle = cleanTitle(title)
        val sanitizedArtist = cleanArtist(rawArtist)
        val art = getArtworkUrl(sanitizedTitle, sanitizedArtist)
        return ResolvedTrackMeta(
            title = sanitizedTitle,
            artist = sanitizedArtist,
            artworkUrl = art
        )
    }

    fun getArtworkUrl(title: String, rawArtist: String?): String? {
        val sanitizedTitle = cleanTitle(title)
        val sanitizedArtist = cleanArtist(rawArtist)
        val cacheKey = "${sanitizedTitle.lowercase()}|${sanitizedArtist.lowercase()}"
        return artworkCache.get(cacheKey)
    }

    fun putArtworkUrl(title: String, rawArtist: String?, url: String) {
        if (url.isNotBlank()) {
            val sanitizedTitle = cleanTitle(title)
            val sanitizedArtist = cleanArtist(rawArtist)
            val cacheKey = "${sanitizedTitle.lowercase()}|${sanitizedArtist.lowercase()}"
            artworkCache.put(cacheKey, url)
        }
    }
}
