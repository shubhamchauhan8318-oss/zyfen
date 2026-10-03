package com.zyfen.music.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zyfen.music.data.local.PlaylistDao
import com.zyfen.music.data.local.SongDao
import com.zyfen.music.data.local.ZyfenDatabase
import com.zyfen.music.data.media.LocalMusicRepository
import com.zyfen.music.data.prefs.SettingsStore
import com.zyfen.music.data.spotify.SpotifyInterceptor
import com.zyfen.music.data.spotify.SpotifyRepository
import com.zyfen.music.data.spotify.SpotifyService
import com.zyfen.music.data.spotify.SpotifySession
import com.zyfen.music.data.youtube.YouTubeSource
import com.zyfen.music.playback.PlayerManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zyfen_settings")

class AppContainer(private val context: Context) {

    val database: ZyfenDatabase by lazy {
        Room.databaseBuilder(context, ZyfenDatabase::class.java, "zyfen.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val songDao: SongDao by lazy { database.songDao() }
    val playlistDao: PlaylistDao by lazy { database.playlistDao() }

    val settingsStore: SettingsStore by lazy { SettingsStore(context.dataStore) }
    val localRepo: LocalMusicRepository by lazy { LocalMusicRepository(context) }
    val youtubeSource: YouTubeSource by lazy { YouTubeSource() }
    val playbackResolver: com.zyfen.music.data.source.PlaybackResolver by lazy {
        com.zyfen.music.data.source.PlaybackResolver(youtubeSource)
    }

    val playerManager: PlayerManager by lazy {
        PlayerManager(context, settingsStore, youtubeSource, songDao, playbackResolver)
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    val plainOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val songDownloader: com.zyfen.music.data.download.SongDownloader by lazy {
        com.zyfen.music.data.download.SongDownloader(context, songDao, youtubeSource)
    }

    val onlineMusicRepo: com.zyfen.music.data.online.OnlineMusicRepository by lazy {
        com.zyfen.music.data.online.OnlineMusicRepository(plainOkHttp)
    }

    val spotifySession: SpotifySession by lazy {
        SpotifySession(plainOkHttp)
    }

    private val apiOkHttp: OkHttpClient by lazy {
        plainOkHttp.newBuilder()
            .addInterceptor(SpotifyInterceptor(spotifySession))
            .build()
    }

    val spotifyService: SpotifyService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .client(apiOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyService::class.java)
    }

    val spotifyRepo: SpotifyRepository by lazy {
        SpotifyRepository(spotifyService, spotifySession, plainOkHttp, songDao, playlistDao)
    }
}
