package com.zyfen.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zyfen.music.data.local.HistoryEntity
import com.zyfen.music.data.local.SongDao
import com.zyfen.music.data.media.Song
import com.zyfen.music.data.media.toEntity
import com.zyfen.music.data.prefs.SettingsStore
import com.zyfen.music.data.source.ArtworkCache
import com.zyfen.music.data.source.PlaybackResolver
import com.zyfen.music.data.source.PlaybackResult
import com.zyfen.music.data.source.TrackIdentity
import com.zyfen.music.data.youtube.YouTubeSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

class PlayerManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val youtube: YouTubeSource,
    private val songDao: SongDao,
    private val resolver: PlaybackResolver = PlaybackResolver(youtube)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    @Volatile private var connecting = false
    private var lostTicks = 0

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _resolveStage = MutableStateFlow("")
    val resolveStage: StateFlow<String> = _resolveStage.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val requestIdGenerator = AtomicLong(0)
    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private var retryJob: Job? = null

    @Volatile private var streamAttempts = 0

    // stream URL -> song id and song id -> last URL
    private val urlToSong = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val songUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    val currentSong: StateFlow<Song?> = combine(_queue, _currentIndex) { q, i ->
        q.getOrNull(i)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            _shuffle.value = settings.shuffle.first()
            settings.repeatMode.first().let { _repeat.value = it }
        }
        connect()
        scope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isConnected) {
                    lostTicks = 0
                    tryFlush()
                    val playing = c.isPlaying
                    _isPlaying.value = playing
                    if (playing) streamAttempts = 0
                    _position.value = c.currentPosition.coerceAtLeast(0)
                    _duration.value = c.duration.coerceAtLeast(0)
                    val id = c.currentMediaItem?.mediaId
                    if (id != null && _queue.value.isNotEmpty()) {
                        val i = _queue.value.indexOfFirst { it.id == id }
                        if (i >= 0 && i != _currentIndex.value) {
                            _currentIndex.value = i
                            recordHistory(_queue.value[i])
                        }
                    }
                } else if (c != null) {
                    lostTicks++
                    if (lostTicks >= 5) {
                        lostTicks = 0
                        controller = null
                        connect()
                    }
                } else {
                    lostTicks++
                    if (lostTicks >= 10 && !connecting) {
                        lostTicks = 0
                        connect()
                    }
                    tryFlush()
                }
                delay(300)
            }
        }
    }

    private fun connect() {
        if (connecting) return
        connecting = true
        try {
            val token = SessionToken(context, ComponentName(context, ZyfenPlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val c = future.get()
                    controller = c
                    c.addListener(object : Player.Listener {
                        override fun onShuffleModeEnabledChanged(enabled: Boolean) { _shuffle.value = enabled }
                        override fun onRepeatModeChanged(mode: Int) { _repeat.value = mode }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                next()
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            val detail = describeError(error)
                            Log.e(TAG, "onPlayerError: code=${error.errorCodeName} detail=$detail msg=${error.message}", error)
                            handlePlaybackError(error, detail)
                        }
                    })
                    c.shuffleModeEnabled = _shuffle.value
                    c.repeatMode = _repeat.value
                    c.volume = _volume.value
                    lostTicks = 0
                    tryFlush()
                } catch (e: Exception) {
                    scope.launch { delay(1500); connect() }
                } finally {
                    connecting = false
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            connecting = false
            scope.launch { delay(1500); connect() }
        }
    }

    private var pendingItems: List<MediaItem>? = null
    private var pendingStartIndex: Int = 0

    private fun tryFlush() {
        val c = controller ?: return
        if (!c.isConnected) return
        val items = pendingItems ?: return
        pendingItems = null
        try {
            c.setMediaItems(items, pendingStartIndex, 0)
            c.prepare()
            c.play()
        } catch (_: Exception) {
            pendingItems = items
        }
    }

    private fun isPlayable(s: Song): Boolean =
        if (s.isLocal) s.contentUri.isNotBlank() && !s.contentUri.startsWith(YouTubeSource.VT) else true

    private fun mediaItem(s: Song, streamUrl: String): MediaItem {
        urlToSong[streamUrl] = s.id
        songUrls[s.id] = streamUrl
        val art = s.artworkUri?.ifBlank { null } ?: ArtworkCache.get(TrackIdentity.fromSong(s))
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(s.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(art?.let { android.net.Uri.parse(it) })
                    .build()
            ).build()
    }

    private fun indexOfItem(c: Player, id: String): Int {
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == id) return i
        }
        return -1
    }

    private fun describeError(error: Throwable): String {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            if (t is HttpDataSource.InvalidResponseCodeException) {
                val host = try { t.dataSpec.uri.host ?: "?" } catch (_: Exception) { "?" }
                return "HTTP ${t.responseCode} @$host"
            }
            t = t.cause
            depth++
        }
        val msg = (error.cause ?: error).message ?: error.javaClass.simpleName
        return msg.take(70)
    }

    private fun extractFailedUrl(error: Throwable): String? {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            if (t is HttpDataSource.HttpDataSourceException) {
                try { return t.dataSpec.uri.toString() } catch (_: Exception) { return null }
            }
            t = t.cause
            depth++
        }
        return null
    }

    private fun recordHistory(song: Song) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                songDao.insertHistory(
                    HistoryEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.durationMs,
                        uri = song.contentUri,
                        artworkUri = song.artworkUri,
                        isLocal = song.isLocal,
                        playedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Resilient stream error handler.
     * Prevents background errors from killing the currently playing song.
     */
    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException, detail: String) {
        val dataUrl = extractFailedUrl(error)
        val errorSong = dataUrl?.let { u ->
            urlToSong[u]?.let { id -> _queue.value.find { it.id == id } }
        }

        val active = currentSong.value
        if (errorSong != null && active != null && errorSong.id != active.id) {
            Log.w(TAG, "Prefetch item failed: '${errorSong.title}' — ignoring to keep active song playing")
            if (dataUrl != null) resolver.markStreamFailed(errorSong.id, dataUrl)
            return
        }

        val song = errorSong ?: active
        if (song == null || song.isLocal) {
            _isPreparing.value = false
            _events.tryEmit("Playback error: $detail")
            return
        }

        val failedUrl = dataUrl ?: songUrls[song.id]
        if (failedUrl != null) {
            resolver.markStreamFailed(song.id, failedUrl)
        }

        streamAttempts++
        if (streamAttempts > MAX_STREAM_ATTEMPTS) {
            resolver.clearFailures(song.id)
            _isPreparing.value = false
            Log.e(TAG, "All stream options exhausted for '${song.title}' — $detail")
            _events.tryEmit("Unable to verify playback source: $detail")
            return
        }

        if (retryJob?.isActive == true) return

        Log.i(TAG, "Retry attempt $streamAttempts/$MAX_STREAM_ATTEMPTS for '${song.title}'")
        _isPreparing.value = true
        _resolveStage.value = "Refreshing audio stream ($streamAttempts/$MAX_STREAM_ATTEMPTS)…"
        retryJob = scope.launch { retryLoop(song) }
    }

    private suspend fun retryLoop(song: Song) {
        val reqId = requestIdGenerator.get()
        try {
            while (
                reqId == requestIdGenerator.get() &&
                currentSong.value?.id == song.id &&
                streamAttempts in 1..MAX_STREAM_ATTEMPTS
            ) {
                val attemptNo = streamAttempts
                _isPreparing.value = true
                _resolveStage.value = "Connecting audio stream ($attemptNo/$MAX_STREAM_ATTEMPTS)…"

                val identity = TrackIdentity.fromSong(song)
                val result = resolver.resolve(identity)
                if (reqId != requestIdGenerator.get()) return

                when (result) {
                    is PlaybackResult.Success -> {
                        if (currentSong.value?.id != song.id) return
                        Log.i(TAG, "Retry $attemptNo: applying fresh verified stream for '${song.title}'")
                        val c = controller
                        if (c != null && c.isConnected) {
                            val idx = indexOfItem(c, song.id)
                            val resumePos = _position.value.coerceAtLeast(0)
                            if (idx >= 0) {
                                c.replaceMediaItem(idx, mediaItem(song, result.streamUrl))
                                c.seekTo(idx, resumePos)
                                c.prepare()
                                c.play()
                            } else {
                                val pos = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
                                c.addMediaItem(pos, mediaItem(song, result.streamUrl))
                                c.seekTo(pos, resumePos)
                                c.prepare()
                                c.play()
                            }
                        } else {
                            pendingItems = listOf(mediaItem(song, result.streamUrl))
                            pendingStartIndex = 0
                            tryFlush()
                        }

                        val deadline = System.currentTimeMillis() + 10_000L
                        while (
                            reqId == requestIdGenerator.get() && streamAttempts == attemptNo &&
                            !_isPlaying.value && currentSong.value?.id == song.id &&
                            System.currentTimeMillis() < deadline
                        ) delay(200)

                        if (_isPlaying.value) {
                            streamAttempts = 0
                            Log.i(TAG, "Retry $attemptNo: stream playback started successfully for '${song.title}'")
                            return
                        }
                        if (streamAttempts != attemptNo) continue
                        Log.w(TAG, "Retry $attemptNo: timeout waiting for audio start")
                        return
                    }
                    is PlaybackResult.Unavailable -> {
                        resolver.clearFailures(song.id)
                        Log.e(TAG, "Retry exhausted: ${result.reason}")
                        _events.tryEmit("Stream unavailable: ${result.reason}")
                        return
                    }
                }
            }
        } finally {
            if (reqId == requestIdGenerator.get()) {
                _resolveStage.value = ""
                _isPreparing.value = false
            }
        }
    }

    private fun persistVideoId(song: Song, streamUrl: String) {
        val vid = youtube.videoIdOf(song)
        val uri = vid?.let { YouTubeSource.VT + it } ?: if (song.isLocal) song.contentUri else return
        if (song.contentUri == uri) return
        scope.launch(Dispatchers.IO) {
            runCatching { songDao.upsert(song.copy(contentUri = uri).toEntity()) }
        }
        _queue.value = _queue.value.map { if (it.id == song.id) it.copy(contentUri = uri) else it }
    }

    /**
     * Primary Playback Dispatcher with Race Condition Cancellation.
     * Guaranteed: When user clicks Song B while Song A is resolving, Song A is cancelled immediately.
     */
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val list = songs.filter { isPlayable(it) }
        if (list.isEmpty()) return
        val start = startIndex.coerceIn(list.indices)

        val reqId = requestIdGenerator.incrementAndGet()
        playJob?.cancel()
        prefetchJob?.cancel()
        retryJob?.cancel()

        streamAttempts = 0
        _queue.value = list
        _currentIndex.value = start
        val target = list[start]
        _isPreparing.value = !target.isLocal
        if (target.durationMs > 0) {
            _duration.value = target.durationMs
        }

        recordHistory(target)

        playJob = scope.launch {
            try {
                _resolveStage.value = "Verifying exact track audio…"
                val identity = TrackIdentity.fromSong(target)
                val result = resolver.resolve(identity)

                if (reqId != requestIdGenerator.get()) {
                    Log.d(TAG, "playSongs: Aborted obsolete request #$reqId for '${target.title}'")
                    return@launch
                }

                when (result) {
                    is PlaybackResult.Success -> {
                        Log.i(TAG, "PLAYING VERIFIED: '${target.title}' by '${target.artist}' (provider=${result.provider})")
                        persistVideoId(target, result.streamUrl)

                        // Update current song metadata with verified artwork
                        if (target.artworkUri.isNullOrBlank() && result.streamUrl.isNotBlank()) {
                            ArtworkCache.get(identity)?.let { verifiedArt ->
                                val enriched = target.copy(artworkUri = verifiedArt)
                                _queue.value = _queue.value.map { if (it.id == target.id) enriched else it }
                            }
                        }

                        pendingItems = listOf(mediaItem(target, result.streamUrl))
                        pendingStartIndex = 0
                        tryFlush()

                        // Prefetch next song in background
                        prefetchNext(reqId, start)

                        if (!target.isLocal) {
                            val deadline = System.currentTimeMillis() + 8_000L
                            while (reqId == requestIdGenerator.get() && _isPreparing.value &&
                                System.currentTimeMillis() < deadline && !_isPlaying.value
                            ) {
                                delay(250)
                            }
                            if (reqId == requestIdGenerator.get()) _isPreparing.value = false
                        }
                    }
                    is PlaybackResult.Unavailable -> {
                        _isPreparing.value = false
                        Log.e(TAG, "Play failed: '${target.title}' — ${result.reason}")
                        _events.tryEmit("Unable to verify track: ${result.reason}")
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "playSongs request #$reqId cancelled")
            } finally {
                if (reqId == requestIdGenerator.get() && retryJob?.isActive != true) {
                    _resolveStage.value = ""
                    _isPreparing.value = false
                }
            }
        }
    }

    private fun prefetchNext(reqId: Long, from: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val list = _queue.value
            val nextIndex = from + 1
            if (nextIndex >= list.size) return@launch
            val nextSong = list[nextIndex]

            if (nextSong.isLocal) {
                var c = controller
                var waited = 0
                while ((c == null || !c.isConnected) && waited < 15 && reqId == requestIdGenerator.get()) {
                    delay(300); waited++
                    c = controller
                }
                val cc = c ?: return@launch
                if (!cc.isConnected || reqId != requestIdGenerator.get()) return@launch
                if (indexOfItem(cc, nextSong.id) < 0) {
                    cc.addMediaItem(cc.mediaItemCount, mediaItem(nextSong, nextSong.contentUri))
                }
                return@launch
            }

            val identity = TrackIdentity.fromSong(nextSong)
            val result = resolver.resolve(identity)
            if (reqId != requestIdGenerator.get()) return@launch

            if (result is PlaybackResult.Success) {
                persistVideoId(nextSong, result.streamUrl)
                var c = controller
                var waited = 0
                while ((c == null || !c.isConnected) && waited < 15 && reqId == requestIdGenerator.get()) {
                    delay(300); waited++
                    c = controller
                }
                val cc = c ?: return@launch
                if (!cc.isConnected || reqId != requestIdGenerator.get()) return@launch
                if (indexOfItem(cc, nextSong.id) < 0) {
                    cc.addMediaItem(cc.mediaItemCount, mediaItem(nextSong, result.streamUrl))
                }
            }
        }
    }

    fun togglePlayPause() {
        tryFlush()
        controller?.let {
            if (it.isConnected) {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    fun next() {
        val list = _queue.value
        if (list.isEmpty()) return

        if (_shuffle.value) {
            val randomIndex = list.indices.random()
            playSongs(list, randomIndex)
            return
        }

        val li = _currentIndex.value
        val nxtIdx = li + 1
        if (nxtIdx < list.size) {
            playSongs(list, nxtIdx)
        } else if (_repeat.value == Player.REPEAT_MODE_ALL) {
            playSongs(list, 0)
        } else {
            // Queue ended
            controller?.pause()
        }
    }

    fun previous() {
        val c = controller
        if (c != null && c.currentPosition > 3000L) {
            c.seekTo(0)
            return
        }
        val list = _queue.value
        if (list.isEmpty()) return

        val li = _currentIndex.value
        val prevIdx = li - 1
        if (prevIdx >= 0) {
            playSongs(list, prevIdx)
        } else {
            playSongs(list, 0)
        }
    }

    fun seekTo(posMs: Long) {
        controller?.seekTo(posMs)
        _position.value = posMs
    }

    fun toggleShuffle() {
        setShuffle(!_shuffle.value)
    }

    fun cycleRepeat() {
        val next = when (_repeat.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeatMode(next)
    }

    fun retryCurrentSong() {
        val s = currentSong.value ?: return
        val idx = _currentIndex.value
        resolver.clearFailures(s.id)
        playSongs(_queue.value, if (idx >= 0) idx else 0)
    }

    fun setShuffle(enabled: Boolean) {
        _shuffle.value = enabled
        controller?.shuffleModeEnabled = enabled
        scope.launch { settings.setShuffle(enabled) }
    }

    fun setRepeatMode(mode: Int) {
        _repeat.value = mode
        controller?.repeatMode = mode
        scope.launch { settings.setRepeatMode(mode) }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        controller?.volume = clamped
    }

    companion object {
        private const val TAG = "PlayerManager"
        private const val MAX_STREAM_ATTEMPTS = 3
    }
}
