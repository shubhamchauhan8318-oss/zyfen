package com.zyfen.music.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zyfen.music.data.local.SongDao
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.media.toEntity
import com.zyfen.music.data.youtube.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val songId: String, val percent: Int) : DownloadState()
    data class Completed(val songId: String, val filePath: String) : DownloadState()
    data class Failed(val songId: String, val error: String) : DownloadState()
}

class SongDownloader(
    private val context: Context,
    private val songDao: SongDao,
    private val youtube: YouTubeSource
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val downloadedDir: File by lazy {
        File(context.filesDir, "offline_tracks").apply { if (!exists()) mkdirs() }
    }

    suspend fun download(song: Song): Boolean = withContext(Dispatchers.IO) {
        if (song.isLocal && !song.filePath.isNullOrBlank()) {
            updateState(song.id, DownloadState.Completed(song.id, song.filePath))
            return@withContext true
        }

        updateState(song.id, DownloadState.Progress(song.id, 5))

        try {
            // Resolve stream url if needed
            val streamUrl = if (song.contentUri.startsWith("http://") || song.contentUri.startsWith("https://")) {
                song.contentUri
            } else {
                updateState(song.id, DownloadState.Progress(song.id, 15))
                youtube.resolve(song)?.url ?: song.contentUri
            }

            if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
                updateState(song.id, DownloadState.Failed(song.id, "Stream URL not available"))
                return@withContext false
            }

            updateState(song.id, DownloadState.Progress(song.id, 30))

            val cleanName = "${song.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.mp3"
            val targetFile = File(downloadedDir, cleanName)

            val req = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    updateState(song.id, DownloadState.Failed(song.id, "HTTP ${response.code}"))
                    return@withContext false
                }

                val body = response.body ?: throw IllegalStateException("Empty body")
                val totalBytes = body.contentLength()
                var readBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes: Int
                        var lastProgress = 30
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            readBytes += bytes
                            if (totalBytes > 0) {
                                val p = 30 + ((readBytes * 65) / totalBytes).toInt()
                                if (p - lastProgress >= 5) {
                                    lastProgress = p
                                    updateState(song.id, DownloadState.Progress(song.id, p))
                                }
                            }
                        }
                    }
                }
            }

            // Save in database as offline song
            val updated = song.copy(
                isLocal = true,
                filePath = targetFile.absolutePath,
                contentUri = Uri.fromFile(targetFile).toString()
            )
            songDao.upsert(updated.toEntity())

            updateState(song.id, DownloadState.Completed(song.id, targetFile.absolutePath))
            true
        } catch (e: Exception) {
            Log.e("SongDownloader", "Download failed for ${song.title}: ${e.message}")
            updateState(song.id, DownloadState.Failed(song.id, e.message ?: "Download failed"))
            false
        }
    }

    private fun updateState(songId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (songId to state)
    }

    fun isDownloaded(song: Song): Boolean {
        if (song.isLocal && !song.filePath.isNullOrBlank() && File(song.filePath).exists()) return true
        val cleanName = "${song.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.mp3"
        return File(downloadedDir, cleanName).exists()
    }
}
