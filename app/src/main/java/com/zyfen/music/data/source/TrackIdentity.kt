package com.zyfen.music.data.source

import com.zyfen.music.data.local.SongEntity
import com.zyfen.music.data.media.Song

/**
 * Immutable, unique Track Identity ensuring track, artist, album,
 * duration, artwork, and playback source stay permanently bound together.
 */
data class TrackIdentity(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val sourceProvider: String, // "local", "youtube", "saavn", "spotify"
    val sourceTrackId: String,
    val contentUri: String,
    val isLocal: Boolean = false,
    val isrc: String? = null,
    val isFavorite: Boolean = false,
    val spotifyId: String? = null,
    val filePath: String? = null
) {
    /**
     * Cache key for artwork and playback caches: "provider:uniqueTrackId"
     */
    val cacheKey: String
        get() = "${sourceProvider.lowercase()}:${sourceTrackId.ifBlank { id }}"

    /**
     * Normalized key used for matching and duplicate detection: "title|artist"
     */
    val normalizedKey: String
        get() = "${normalizeString(title)}|${normalizeString(artist)}"

    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        artworkUri = artworkUrl,
        isLocal = isLocal,
        isFavorite = isFavorite,
        spotifyId = spotifyId,
        filePath = filePath
    )

    companion object {
        fun fromSong(song: Song): TrackIdentity {
            val provider = when {
                song.isLocal -> "local"
                song.id.startsWith("yt_") -> "youtube"
                song.id.startsWith("saavn_") -> "saavn"
                song.id.startsWith("spotify_") -> "spotify"
                song.contentUri.startsWith("vt:") || song.contentUri.startsWith("yt:") -> "youtube"
                else -> if (song.isLocal) "local" else "online"
            }
            val trackId = when {
                song.id.startsWith("yt_") -> song.id.removePrefix("yt_")
                song.id.startsWith("saavn_") -> song.id.removePrefix("saavn_")
                song.id.startsWith("spotify_") -> song.id.removePrefix("spotify_")
                song.id.startsWith("local_") -> song.id.removePrefix("local_")
                song.contentUri.startsWith("vt:") -> song.contentUri.removePrefix("vt:")
                song.contentUri.startsWith("yt:") -> song.contentUri.removePrefix("yt:")
                else -> song.id
            }
            return TrackIdentity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                artworkUrl = song.artworkUri,
                sourceProvider = provider,
                sourceTrackId = trackId,
                contentUri = song.contentUri,
                isLocal = song.isLocal,
                isFavorite = song.isFavorite,
                spotifyId = song.spotifyId,
                filePath = song.filePath
            )
        }

        fun normalizeString(input: String): String {
            var s = input.lowercase().trim()
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            s = Regex("""\p{InCombiningDiacriticalMarks}+""").replace(s, "")
            s = s.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
            s = s.replace(Regex("""[^a-z0-9\s]"""), " ")
            return s.split(Regex("""\s+""")).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
}
