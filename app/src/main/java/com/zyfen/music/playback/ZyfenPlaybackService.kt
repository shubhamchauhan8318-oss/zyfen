package com.zyfen.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zyfen.music.MainActivity
import com.zyfen.music.ZyfenApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ZyfenPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * ZYFEN is a music player: audio only to prevent device-specific
     * video codec initialization crashes when streaming muxed audio/video containers.
     */
    private class AudioOnlyRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
        override fun buildVideoRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            eventHandler: Handler,
            eventListener: VideoRendererEventListener,
            allowedVideoJoiningTimeMs: Long,
            out: ArrayList<Renderer>
        ) {
            // Audio only
        }
    }

    /**
     * Ensures Next and Previous commands are ALWAYS available in System Notification,
     * lock screen, Android Auto, and Bluetooth media controls, forwarding clicks
     * directly to the active PlayerManager playlist queue.
     */
    private class ZyfenForwardingPlayer(
        player: Player,
        private val onNextAction: () -> Unit,
        private val onPrevAction: () -> Unit
    ) : ForwardingPlayer(player) {

        override fun isCommandAvailable(command: Int): Boolean {
            if (command == Player.COMMAND_SEEK_TO_NEXT ||
                command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            ) {
                return true
            }
            return super.isCommandAvailable(command)
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun seekToNext() {
            onNextAction()
        }

        override fun seekToNextMediaItem() {
            onNextAction()
        }

        override fun seekToPrevious() {
            onPrevAction()
        }

        override fun seekToPreviousMediaItem() {
            onPrevAction()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpData = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept-Encoding" to "identity"
                )
            )

        // CRITICAL FIX: DefaultDataSource supports content://, file://, assets AND http(s)://
        val dataSourceFactory = DefaultDataSource.Factory(this, httpData)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer 30 seconds
                120_000, // Max buffer 2 minutes
                1_500, // 1.5s playback start buffer
                3_000 // 3s buffer after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val basePlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(AudioOnlyRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Auto handle audio focus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val forwardingPlayer = ZyfenForwardingPlayer(
            player = basePlayer,
            onNextAction = {
                serviceScope.launch(Dispatchers.Main) {
                    runCatching { ZyfenApp.container.playerManager.next() }
                }
            },
            onPrevAction = {
                serviceScope.launch(Dispatchers.Main) {
                    runCatching { ZyfenApp.container.playerManager.previous() }
                }
            }
        )

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            serviceScope.launch(Dispatchers.Main) {
                                runCatching { ZyfenApp.container.playerManager.next() }
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            serviceScope.launch(Dispatchers.Main) {
                                runCatching { ZyfenApp.container.playerManager.previous() }
                            }
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        session = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
        setMediaNotificationProvider(notificationProvider)

        basePlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    AudioEffectsManager.attach(audioSessionId)
                }
            }
        })

        if (basePlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET && basePlayer.audioSessionId != 0) {
            AudioEffectsManager.attach(basePlayer.audioSessionId)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        serviceScope.cancel()
        AudioEffectsManager.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
