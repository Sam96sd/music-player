package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.data.local.PulseWaveDatabase
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCountedTrackId: Long? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull() ?: return
                if (exoPlayer.playWhenReady && trackId != lastCountedTrackId) {
                    lastCountedTrackId = trackId
                    serviceScope.launch {
                        runCatching {
                            PulseWaveDatabase.getInstance(applicationContext)
                                .musicDao()
                                .incrementPlayCount(trackId)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val trackId = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                    if (trackId != lastCountedTrackId) {
                        lastCountedTrackId = trackId
                        serviceScope.launch {
                            runCatching {
                                PulseWaveDatabase.getInstance(applicationContext)
                                    .musicDao()
                                    .incrementPlayCount(trackId)
                            }
                        }
                    }
                }
            }
        })

        val forwardingPlayer = ShuffleAwareForwardingPlayer(exoPlayer)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(MuffinArtworkBitmapLoader(this))
            .setCallback(PlaybackSessionCallback())
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_music_note)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class ShuffleAwareForwardingPlayer(
        private val wrapped: ExoPlayer
    ) : ForwardingPlayer(wrapped) {

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) {
                val count = wrapped.mediaItemCount
                if (count > 0) {
                    val anchoredOrder = FixedShuffleOrder.buildAnchoredFisherYates(
                        size = count,
                        anchorIndex = wrapped.currentMediaItemIndex
                    )
                    wrapped.setShuffleOrder(FixedShuffleOrder(anchoredOrder))
                }
                wrapped.shuffleModeEnabled = true
            } else {
                wrapped.shuffleModeEnabled = false
            }
        }
    }

    private inner class PlaybackSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(MediaItemMapper.CMD_PLAY_WITH_SHUFFLE_ORDER)
                .add(MediaItemMapper.CMD_ENABLE_ANCHORED_SHUFFLE)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                MediaItemMapper.ACTION_PLAY_WITH_SHUFFLE_ORDER -> {
                    val count = exoPlayer.mediaItemCount
                    if (count > 0) {
                        val provided = args.getIntArray(MediaItemMapper.EXTRA_SHUFFLED_INDICES)
                        val validOrder = if (provided != null && provided.size == count) {
                            provided
                        } else {
                            FixedShuffleOrder.buildAnchoredFisherYates(count, -1)
                        }
                        exoPlayer.setShuffleOrder(FixedShuffleOrder(validOrder))
                        exoPlayer.shuffleModeEnabled = true
                        if (exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        }
                        val firstIdx = validOrder.firstOrNull()?.takeIf { it in 0 until count } ?: 0
                        exoPlayer.seekTo(firstIdx, 0L)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                MediaItemMapper.ACTION_ENABLE_ANCHORED_SHUFFLE -> {
                    val count = exoPlayer.mediaItemCount
                    if (count > 0) {
                        val provided = args.getIntArray(MediaItemMapper.EXTRA_SHUFFLED_INDICES)
                        val validOrder = if (provided != null && provided.size == count) {
                            provided
                        } else {
                            FixedShuffleOrder.buildAnchoredFisherYates(
                                size = count,
                                anchorIndex = exoPlayer.currentMediaItemIndex
                            )
                        }
                        exoPlayer.setShuffleOrder(FixedShuffleOrder(validOrder))
                        exoPlayer.shuffleModeEnabled = true
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
