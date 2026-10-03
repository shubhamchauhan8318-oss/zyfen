package com.zyfen.music.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.zyfen.music.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val artworkUri: String?,
    val isLocal: Boolean = true,
    val isFavorite: Boolean = false,
    val spotifyId: String? = null,
    val spotifyUrl: String? = null,
    val filePath: String? = null
)

fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    contentUri = uri,
    artworkUri = artworkUri,
    isLocal = isLocal,
    isFavorite = isFavorite,
    spotifyId = spotifyId,
    spotifyUrl = spotifyUrl,
    filePath = filePath
)

fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    uri = contentUri,
    artworkUri = artworkUri,
    isLocal = isLocal,
    isFavorite = isFavorite,
    spotifyId = spotifyId,
    spotifyUrl = spotifyUrl,
    filePath = filePath
)

class LocalMusicRepository(private val context: Context) {
    suspend fun loadLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Support MP3, M4A, FLAC, WAV, OGG with duration >= 5000 ms to avoid system ringtones/sound effects
        val sel = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} >= 5000"

        try {
            context.contentResolver.query(
                collection,
                proj,
                sel,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri: Uri = ContentUris.withAppendedId(collection, id)
                    val albumId = c.getLong(albumIdCol)
                    val art = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                    val path = if (dataCol >= 0) c.getString(dataCol) else null

                    val rawTitle = c.getString(titleCol)?.trim().orEmpty()
                    val title = if (rawTitle.isNotBlank()) rawTitle else (path?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Track $id")
                    val rawArtist = c.getString(artistCol)?.trim().orEmpty()
                    val artist = if (rawArtist.isNotBlank() && rawArtist != "<unknown>") rawArtist else "Unknown Artist"
                    val rawAlbum = c.getString(albumCol)?.trim().orEmpty()
                    val album = if (rawAlbum.isNotBlank() && rawAlbum != "<unknown>") rawAlbum else "Unknown Album"

                    songs += Song(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(durCol),
                        contentUri = uri.toString(),
                        artworkUri = art,
                        isLocal = true,
                        filePath = path
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalMusicRepo", "Error reading local music", e)
        }
        songs
    }
}
