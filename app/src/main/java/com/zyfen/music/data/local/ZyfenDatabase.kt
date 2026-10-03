package com.zyfen.music.data.local

import androidx.room.*

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val isLocal: Boolean,
    val isFavorite: Boolean = false,
    val spotifyId: String? = null,
    val spotifyUrl: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val filePath: String? = null
)

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val isLocal: Boolean,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
    val isSpotifyImport: Boolean = false,
    val spotifyId: String? = null,
    val spotifyUrl: String? = null,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["playlistId"])
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int = 0
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    suspend fun favorites(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE songs SET artworkUri = :artworkUri WHERE id = :id")
    suspend fun updateArtwork(id: String, artworkUri: String)

    @Query("UPDATE songs SET title = :title, artist = :artist, artworkUri = :artworkUri WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, artist: String, artworkUri: String?)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: String)

    // History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 100")
    suspend fun getRecentHistory(): List<HistoryEntity>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Query("DELETE FROM playback_history WHERE historyId = :id")
    suspend fun deleteHistoryItem(id: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY dateCreated DESC")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun withSongs(id: String): PlaylistWithSongs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PlaylistEntity)

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: String, newName: String)

    @Query("UPDATE playlists SET artworkUrl = :newArtwork WHERE id = :id")
    suspend fun updatePlaylistArtwork(id: String, newArtwork: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSong(ref: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongs(refs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid")
    suspend fun clearSongs(pid: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid")
    suspend fun removeSong(pid: String, sid: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ZyfenDatabase : androidx.room.RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
}
