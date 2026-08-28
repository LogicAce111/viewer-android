package com.legion.viewer.playback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.legion.viewer.MainActivity
import com.legion.viewer.R
import com.legion.viewer.ViewerApplication
import com.legion.viewer.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlaybackController
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeAfterFocusGain = false
    private var ducked = false
    private var noisyRegistered = false

    private val becomingNoisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                controller.pauseFromService()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = (application as ViewerApplication).container.playback
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
        mediaSession = MediaSessionCompat(this, "ViewerPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (requestAudioFocus()) this@PlaybackService.controller.playFromService()
                    else this@PlaybackService.controller.reportAudioFocusDenied()
                }

                override fun onPause() = this@PlaybackService.controller.pauseFromService()
                override fun onSkipToNext() = this@PlaybackService.controller.next()
                override fun onSkipToPrevious() = this@PlaybackService.controller.previous()
                override fun onSeekTo(pos: Long) = this@PlaybackService.controller.seekTo(pos)
            })
            isActive = true
        }
        ContextCompat.registerReceiver(
            this,
            becomingNoisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
        scope.launch {
            controller.snapshot.collectLatest { snapshot ->
                val state = when (snapshot.state) {
                    PlaybackState.Idle -> PlaybackStateCompat.STATE_NONE
                    PlaybackState.Opening -> PlaybackStateCompat.STATE_BUFFERING
                    PlaybackState.Playing -> PlaybackStateCompat.STATE_PLAYING
                    PlaybackState.Paused -> PlaybackStateCompat.STATE_PAUSED
                    PlaybackState.Ended -> PlaybackStateCompat.STATE_STOPPED
                    PlaybackState.Error -> PlaybackStateCompat.STATE_ERROR
                }
                mediaSession.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.current?.title ?: "Viewer")
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.durationMs)
                        .build()
                )
                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(state, snapshot.positionMs, if (state == PlaybackStateCompat.STATE_PLAYING) controller.rate else 0f)
                if (snapshot.state == PlaybackState.Error) {
                    playbackState.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, snapshot.message)
                }
                mediaSession.setPlaybackState(playbackState.build())
                if (snapshot.current?.category?.name == "Music") updateNotificationIfAllowed()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        when (intent?.action) {
            ACTION_PLAY, ACTION_START -> {
                if (requestAudioFocus()) controller.playFromService() else controller.reportAudioFocusDenied()
            }
            ACTION_PAUSE -> controller.pauseFromService()
            ACTION_PLAY_PAUSE -> {
                if (controller.snapshot.value.state == PlaybackState.Playing) {
                    controller.pauseFromService()
                } else if (requestAudioFocus()) {
                    controller.playFromService()
                } else {
                    controller.reportAudioFocusDenied()
                }
            }
            ACTION_NEXT -> controller.next()
            ACTION_PREVIOUS -> controller.previous()
            ACTION_STOP -> {
                controller.stopFromService()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (noisyRegistered) unregisterReceiver(becomingNoisy)
        controller.applyFocusDucking(false)
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val snapshot = controller.snapshot.value
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(snapshot.current?.title ?: "Viewer")
            .setContentText(playbackText(snapshot.state))
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot.state == PlaybackState.Playing || snapshot.state == PlaybackState.Opening)
            .addAction(R.drawable.ic_notification, "上一首", action(ACTION_PREVIOUS, 1))
            .addAction(
                R.drawable.ic_notification,
                if (snapshot.state == PlaybackState.Playing) "暂停" else "播放",
                action(if (snapshot.state == PlaybackState.Playing) ACTION_PAUSE else ACTION_PLAY, 2),
            )
            .addAction(R.drawable.ic_notification, "下一首", action(ACTION_NEXT, 3))
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun updateNotificationIfAllowed() {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification())
        }.onFailure {
            val category = controller.snapshot.value.current?.category?.name ?: "Unknown"
            (application as ViewerApplication).container.log.errorType("playback-notification-$category", it)
        }
    }

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun requestAudioFocus(): Boolean {
        if (audioFocusRequest == null) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(::onAudioFocusChanged)
                .build()
        }
        return audioFocusRequest?.let(audioManager::requestAudioFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = controller.snapshot.value.state == PlaybackState.Playing
                if (resumeAfterFocusGain) controller.pauseFromService()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                controller.applyFocusDucking(true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                ducked = false
                controller.applyFocusDucking(false)
                controller.pauseFromService()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    ducked = false
                    controller.applyFocusDucking(false)
                }
                if (resumeAfterFocusGain && controller.snapshot.value.state != PlaybackState.Playing) {
                    controller.playFromService()
                }
                resumeAfterFocusGain = false
            }
        }
    }

    private fun playbackText(state: PlaybackState): String = when (state) {
        PlaybackState.Idle -> "未播放"
        PlaybackState.Opening -> "正在准备"
        PlaybackState.Playing -> "正在播放"
        PlaybackState.Paused -> "已暂停"
        PlaybackState.Ended -> "播放完成"
        PlaybackState.Error -> "播放失败"
    }

    companion object {
        const val ACTION_START = "com.legion.viewer.START"
        const val ACTION_PLAY = "com.legion.viewer.PLAY"
        const val ACTION_PAUSE = "com.legion.viewer.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.legion.viewer.PLAY_PAUSE"
        const val ACTION_NEXT = "com.legion.viewer.NEXT"
        const val ACTION_PREVIOUS = "com.legion.viewer.PREVIOUS"
        const val ACTION_STOP = "com.legion.viewer.STOP"
        private const val CHANNEL_ID = "viewer_playback"
        private const val NOTIFICATION_ID = 1201
    }
}
