package com.legion.viewer.playback

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.legion.viewer.data.LocalLog
import com.legion.viewer.data.PreferencesRepository
import com.legion.viewer.data.ProgressRepository
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.PlaybackOpenStage
import com.legion.viewer.model.PlaybackOrder
import com.legion.viewer.model.PlaybackSnapshot
import com.legion.viewer.model.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class PlaybackController(
    private val application: Application,
    private val preferences: PreferencesRepository,
    private val progress: ProgressRepository,
    private val log: LocalLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val libVlc = LibVLC(application, arrayListOf("--audio-time-stretch", "--no-stats"))
    private val player = MediaPlayer(libVlc)
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private var queue = emptyList<MediaItem>()
    private var currentIndex = -1
    private var order = PlaybackOrder.Sequential
    private var pendingSeek = 0L
    private var attachedLayout: VLCVideoLayout? = null
    private var currentSource: SafMediaSource? = null
    private var openJob: Job? = null
    private var openingTimeoutJob: Job? = null
    private var requestVersion = 0L
    private var softwareRetryAttempted = false

    var volume: Int = 80
        private set
    var muted: Boolean = false
        private set
    var rate: Float = 1f
        private set

    init {
        player.setEventListener { event ->
            val type = event.type
            scope.launch { handlePlayerEvent(type) }
        }
        scope.launch {
            preferences.settings.collectLatest { settings ->
                volume = settings.volume
                muted = settings.muted
                rate = settings.playbackRate
                order = settings.playbackOrder
                applyAudioSettings()
            }
        }
        scope.launch {
            while (true) {
                delay(5_000)
                if (_snapshot.value.state == PlaybackState.Playing) saveCurrentProgress()
            }
        }
    }

    fun open(items: List<MediaItem>, item: MediaItem, autoPlay: Boolean = true, savePrevious: Boolean = true) {
        if (items.isEmpty()) return
        val version = ++requestVersion
        openJob?.cancel()
        openingTimeoutJob?.cancel()
        openJob = scope.launch {
            if (savePrevious) saveCurrentProgress()
            stopPlayerAndCloseSource()
            queue = items
            currentIndex = items.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            val target = queue[currentIndex]
            softwareRetryAttempted = false
            pendingSeek = progress.get(target.category, target.uri)?.positionMs ?: 0L
            openTarget(version, target, autoPlay, softwareDecode = false)
        }
    }

    fun playPause() {
        if (_snapshot.value.state == PlaybackState.Playing) pause() else play()
    }

    fun play() {
        val current = _snapshot.value.current ?: return
        if (_snapshot.value.state == PlaybackState.Error) return
        if (current.category == MediaCategory.Music) {
            startAudioService(PlaybackService.ACTION_PLAY)
        } else {
            playFromService()
        }
    }

    fun pause() {
        if (_snapshot.value.state != PlaybackState.Playing && _snapshot.value.state != PlaybackState.Opening) return
        runCatching { player.pause() }
            .onFailure { error ->
                logPlaybackError("pause", _snapshot.value.current?.category, error)
            }
        scope.launch { saveCurrentProgress() }
    }

    internal fun playFromService() {
        when (_snapshot.value.state) {
            PlaybackState.Ended -> {
                player.time = 0
                player.play()
            }
            PlaybackState.Error, PlaybackState.Idle -> Unit
            else -> player.play()
        }
    }

    internal fun pauseFromService() = pause()

    internal fun applyFocusDucking(ducked: Boolean) {
        player.volume = when {
            muted -> 0
            ducked -> (volume / 4).coerceAtLeast(1)
            else -> volume
        }
    }

    fun retry() {
        val current = _snapshot.value.current ?: return
        open(queue, current, autoPlay = true, savePrevious = false)
    }

    fun seekTo(positionMs: Long) {
        if (_snapshot.value.canSeek && player.isSeekable) {
            player.time = positionMs.coerceIn(0, player.length.coerceAtLeast(0))
            publishPosition()
        }
    }

    fun seekBy(deltaMs: Long) = seekTo(player.time + deltaMs)
    fun previous() = moveTo(if (currentIndex <= 0) queue.lastIndex else currentIndex - 1)
    fun next() = moveTo(nextIndex())

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        player.volume = if (muted) 0 else volume
        scope.launch { preferences.setVolume(volume) }
    }

    fun toggleMute() {
        muted = !muted
        player.volume = if (muted) 0 else volume
        scope.launch { preferences.setMuted(muted) }
    }

    fun setRate(value: Float) {
        rate = value.coerceIn(.5f, 2f)
        player.rate = rate
        scope.launch { preferences.setRate(rate) }
    }

    fun setOrder(value: PlaybackOrder) {
        order = value
        scope.launch { preferences.setOrder(value) }
    }

    fun attachVideo(layout: VLCVideoLayout) {
        if (attachedLayout === layout) return
        detachVideo()
        attachedLayout = layout
        player.attachViews(layout, null, false, false)
    }

    fun detachVideo() {
        if (attachedLayout != null) runCatching { player.detachViews() }
        attachedLayout = null
    }

    fun pauseVideoForBackground() {
        if (_snapshot.value.isVideo && player.isPlaying) pause()
    }

    fun stop() {
        requestVersion++
        openJob?.cancel()
        openingTimeoutJob?.cancel()
        scope.launch {
            saveCurrentProgress()
            stopPlayerAndCloseSource()
            detachVideo()
            queue = emptyList()
            currentIndex = -1
            _snapshot.value = PlaybackSnapshot()
            application.stopService(Intent(application, PlaybackService::class.java))
        }
    }

    internal fun stopFromService() {
        requestVersion++
        openJob?.cancel()
        openingTimeoutJob?.cancel()
        scope.launch {
            saveCurrentProgress()
            stopPlayerAndCloseSource()
            detachVideo()
            queue = emptyList()
            currentIndex = -1
            _snapshot.value = PlaybackSnapshot()
        }
    }

    internal fun reportAudioFocusDenied() {
        val current = _snapshot.value.current ?: return
        log.errorType("audio-focus-${current.category.name}", IllegalStateException("Audio focus request was denied"))
        _snapshot.value = _snapshot.value.copy(
            state = PlaybackState.Error,
            canRetry = true,
            openStage = PlaybackOpenStage.None,
            message = "暂时无法获得音频播放权限，请稍后重试。",
        )
    }

    private suspend fun openTarget(version: Long, target: MediaItem, autoPlay: Boolean, softwareDecode: Boolean) {
        if (version != requestVersion) return
        _snapshot.value = PlaybackSnapshot(
            state = PlaybackState.Opening,
            current = target,
            queue = queue,
            isVideo = target.category == MediaCategory.Video,
            openStage = if (softwareDecode) PlaybackOpenStage.RetryingSoftware else PlaybackOpenStage.Accessing,
        )

        var openedSource: SafMediaSource? = null
        try {
            openedSource = withContext(Dispatchers.IO) {
                SafMediaSource.open(application.contentResolver, target.uri)
            }
            coroutineContext.ensureActive()
            if (version != requestVersion) {
                openedSource.close()
                return
            }

            val media: Media = openedSource.createMedia(libVlc)
            if (target.category == MediaCategory.Video) {
                media.setHWDecoderEnabled(!softwareDecode, false)
            }
            player.media = media
            media.release()
            currentSource = openedSource
            openedSource = null
            _snapshot.value = _snapshot.value.copy(
                state = PlaybackState.Opening,
                openStage = if (softwareDecode) PlaybackOpenStage.RetryingSoftware else PlaybackOpenStage.Preparing,
                message = if (softwareDecode) "硬件解码失败，正在尝试兼容模式…" else "",
            )
            startOpeningTimeout(version)
            if (autoPlay) play()
        } catch (cancelled: CancellationException) {
            openedSource?.close()
            throw cancelled
        } catch (error: Throwable) {
            openedSource?.close()
            if (version == requestVersion) failOpen(target, error)
        }
    }

    private suspend fun handlePlayerEvent(type: Int) {
        when (type) {
            MediaPlayer.Event.Opening -> {
                if (_snapshot.value.state != PlaybackState.Error) {
                    _snapshot.value = _snapshot.value.copy(state = PlaybackState.Opening)
                }
            }
            MediaPlayer.Event.Playing -> {
                openingTimeoutJob?.cancel()
                if (pendingSeek > 0 && pendingSeek < player.length) player.time = pendingSeek
                pendingSeek = 0L
                applyAudioSettings()
                _snapshot.value = _snapshot.value.copy(
                    state = PlaybackState.Playing,
                    canSeek = player.isSeekable,
                    canRetry = false,
                    openStage = PlaybackOpenStage.None,
                    message = "",
                )
                publishPosition()
            }
            MediaPlayer.Event.Paused -> {
                openingTimeoutJob?.cancel()
                _snapshot.value = _snapshot.value.copy(
                    state = PlaybackState.Paused,
                    canSeek = player.isSeekable,
                    openStage = PlaybackOpenStage.None,
                )
                publishPosition()
            }
            MediaPlayer.Event.Stopped -> {
                if (_snapshot.value.state != PlaybackState.Opening && _snapshot.value.current == null) {
                    _snapshot.value = PlaybackSnapshot()
                }
            }
            MediaPlayer.Event.EndReached -> onEnded()
            MediaPlayer.Event.EncounteredError -> handlePlaybackFailure("媒体无法解码或文件已经损坏。")
            MediaPlayer.Event.TimeChanged, MediaPlayer.Event.LengthChanged -> publishPosition()
        }
    }

    private fun startOpeningTimeout(version: Long) {
        openingTimeoutJob?.cancel()
        openingTimeoutJob = scope.launch {
            delay(12_000)
            if (version == requestVersion && _snapshot.value.state == PlaybackState.Opening) {
                handlePlaybackFailure("打开媒体超时，请检查文件是否可读。")
            }
        }
    }

    private suspend fun handlePlaybackFailure(message: String) {
        val current = _snapshot.value.current ?: return
        openingTimeoutJob?.cancel()
        if (current.category == MediaCategory.Video && !softwareRetryAttempted) {
            softwareRetryAttempted = true
            val resumeAt = player.time.coerceAtLeast(pendingSeek)
            stopPlayerAndCloseSource()
            pendingSeek = resumeAt
            openTarget(requestVersion, current, autoPlay = true, softwareDecode = true)
            return
        }
        logPlaybackError("decode", current.category, IllegalStateException("LibVlcEncounteredError"))
        stopPlayerAndCloseSource()
        _snapshot.value = _snapshot.value.copy(
            state = PlaybackState.Error,
            canSeek = false,
            canRetry = true,
            openStage = PlaybackOpenStage.None,
            message = message,
        )
    }

    private suspend fun failOpen(target: MediaItem, error: Throwable) {
        openingTimeoutJob?.cancel()
        stopPlayerAndCloseSource()
        logPlaybackError("open", target.category, error)
        _snapshot.value = _snapshot.value.copy(
            state = PlaybackState.Error,
            canSeek = false,
            canRetry = true,
            openStage = PlaybackOpenStage.None,
            message = when (error) {
                is SecurityException -> "目录授权已失效，请重新选择目录。"
                else -> "无法读取这个媒体文件。"
            },
        )
    }

    private fun onEnded() {
        openingTimeoutJob?.cancel()
        val current = _snapshot.value.current
        if (current != null) scope.launch { progress.savePlayback(current, 0, completed = true) }
        if (queue.isEmpty()) {
            _snapshot.value = _snapshot.value.copy(state = PlaybackState.Ended, canSeek = false)
            return
        }
        when (order) {
            PlaybackOrder.RepeatOne -> open(queue, queue[currentIndex], true, savePrevious = false)
            PlaybackOrder.Sequential, PlaybackOrder.Shuffle -> moveTo(nextIndex(), saveCurrent = false)
        }
    }

    private fun moveTo(index: Int, saveCurrent: Boolean = true) {
        if (queue.isNotEmpty() && index in queue.indices) open(queue, queue[index], true, savePrevious = saveCurrent)
    }

    private fun nextIndex(): Int = when {
        queue.size <= 1 -> 0
        order == PlaybackOrder.Shuffle -> generateSequence { Random.nextInt(queue.size) }.first { it != currentIndex }
        currentIndex >= queue.lastIndex -> 0
        else -> currentIndex + 1
    }

    private fun applyAudioSettings() {
        runCatching {
            player.volume = if (muted) 0 else volume
            player.rate = rate
        }.onFailure {
            log.errorType("playback-settings-${_snapshot.value.current?.category?.name ?: "Unknown"}", it)
        }
    }

    private fun publishPosition() {
        val state = _snapshot.value.state
        if (_snapshot.value.current == null || state == PlaybackState.Error) return
        _snapshot.value = _snapshot.value.copy(
            positionMs = player.time.coerceAtLeast(0),
            durationMs = player.length.coerceAtLeast(0),
            canSeek = player.isSeekable && player.length > 0,
        )
    }

    private suspend fun saveCurrentProgress() {
        val current = _snapshot.value.current ?: return
        runCatching { progress.savePlayback(current, player.time.coerceAtLeast(0)) }
            .onFailure { log.errorType("playback-progress-${current.category.name}", it) }
    }

    private fun stopPlayerAndCloseSource() {
        runCatching { player.stop() }
        currentSource?.close()
        currentSource = null
    }

    private fun startAudioService(action: String) {
        runCatching {
            ContextCompat.startForegroundService(
                application,
                Intent(application, PlaybackService::class.java).setAction(action),
            )
        }.onFailure {
            log.errorType("playback-service-${_snapshot.value.current?.category?.name ?: "Unknown"}", it)
            _snapshot.value = _snapshot.value.copy(
                state = PlaybackState.Error,
                canRetry = true,
                openStage = PlaybackOpenStage.None,
                message = "无法启动后台音乐服务。",
            )
        }
    }

    private fun logPlaybackError(stage: String, category: MediaCategory?, error: Throwable) {
        log.errorType("playback-$stage-${category?.name ?: "Unknown"}", error)
    }
}
