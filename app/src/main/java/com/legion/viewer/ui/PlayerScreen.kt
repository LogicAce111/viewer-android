@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.legion.viewer.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.PlaybackOpenStage
import com.legion.viewer.model.PlaybackOrder
import com.legion.viewer.model.PlaybackSnapshot
import com.legion.viewer.model.PlaybackState
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

private enum class PlayerControlLayer { Transport, Advanced }

@Composable
fun PlayerScreen(model: ViewerViewModel, onBack: () -> Unit) {
    val snapshot by model.playback.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val controller = model.container.playback
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember(snapshot.current?.uri) { mutableStateOf(true) }
    var controlLayer by remember(snapshot.current?.uri) { mutableStateOf(PlayerControlLayer.Transport) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var activelySeeking by remember { mutableStateOf(false) }

    fun registerInteraction() {
        interactionVersion += 1
    }

    fun revealTransport() {
        controlLayer = PlayerControlLayer.Transport
        controlsVisible = true
        registerInteraction()
    }

    fun setFullscreen(value: Boolean) {
        fullscreen = value
        activity?.let {
            it.requestedOrientation = if (value && snapshot.isVideo) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                if (value) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        when {
            controlLayer == PlayerControlLayer.Advanced -> {
                controlLayer = PlayerControlLayer.Transport
                controlsVisible = true
                registerInteraction()
            }
            fullscreen -> setFullscreen(false)
            else -> onBack()
        }
    }

    LaunchedEffect(snapshot.state, snapshot.isVideo) {
        if (snapshot.isVideo && snapshot.state != PlaybackState.Playing) controlsVisible = true
    }
    LaunchedEffect(
        snapshot.isVideo,
        snapshot.state,
        controlsVisible,
        controlLayer,
        activelySeeking,
        interactionVersion,
    ) {
        if (
            snapshot.isVideo &&
            snapshot.state == PlaybackState.Playing &&
            controlsVisible &&
            controlLayer == PlayerControlLayer.Transport &&
            !activelySeeking
        ) {
            delay(3_000)
            controlsVisible = false
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.pauseVideoForBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.detachVideo()
            if (fullscreen) setFullscreen(false)
        }
    }

    val commonControls = PlayerControlCallbacks(
        onSeek = controller::seekTo,
        onPlayPause = controller::playPause,
        onPrevious = controller::previous,
        onNext = controller::next,
        onBackFive = { controller.seekBy(-5_000) },
        onForwardFive = { controller.seekBy(5_000) },
        onMute = controller::toggleMute,
        onVolume = controller::setVolume,
        onRate = controller::setRate,
        onOrder = model::setPlaybackOrder,
        onFullscreen = {
            setFullscreen(!fullscreen)
            controlsVisible = true
            registerInteraction()
        },
    )

    if (snapshot.isVideo) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { VLCVideoLayout(it).also(controller::attachVideo) },
                update = controller::attachVideo,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(snapshot.current?.uri, snapshot.state) {
                        detectTapGestures(
                            onTap = {
                                if (controlLayer == PlayerControlLayer.Advanced) {
                                    controlLayer = PlayerControlLayer.Transport
                                    controlsVisible = false
                                } else {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) registerInteraction()
                                }
                            },
                            onDoubleTap = {
                                if (snapshot.state !in setOf(PlaybackState.Opening, PlaybackState.Error, PlaybackState.Idle)) {
                                    controller.playPause()
                                }
                                revealTransport()
                            },
                        )
                    },
            )
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
            ) {
                VideoTitleOverlay(
                    title = snapshot.current?.title ?: "播放器",
                    state = snapshot.state,
                    onBack = { if (fullscreen) setFullscreen(false) else onBack() },
                    advanced = controlLayer == PlayerControlLayer.Advanced,
                    onAdvanced = {
                        controlLayer = if (controlLayer == PlayerControlLayer.Advanced) {
                            PlayerControlLayer.Transport
                        } else {
                            PlayerControlLayer.Advanced
                        }
                        controlsVisible = true
                        registerInteraction()
                    },
                )
            }
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                PlayerBottomLayer(
                    snapshot = snapshot,
                    settings = settings,
                    fullscreen = fullscreen,
                    layer = controlLayer,
                    videoOverlay = true,
                    callbacks = commonControls,
                    onInteraction = ::registerInteraction,
                    onSeekingChanged = { activelySeeking = it },
                )
            }
            PlaybackStateOverlay(snapshot, true, Modifier.fillMaxSize(), onBack, controller::retry)
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                snapshot.current?.title ?: "播放器",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(playbackLabel(snapshot.state), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(ViewerIcons.Back, "返回") } },
                    actions = {
                        IconButton(onClick = {
                            controlLayer = if (controlLayer == PlayerControlLayer.Advanced) {
                                PlayerControlLayer.Transport
                            } else {
                                PlayerControlLayer.Advanced
                            }
                        }) {
                            Icon(ViewerIcons.Tune, if (controlLayer == PlayerControlLayer.Advanced) "关闭播放设置" else "打开播放设置")
                        }
                    },
                )
            },
            bottomBar = {
                PlayerBottomLayer(
                    snapshot = snapshot,
                    settings = settings,
                    fullscreen = fullscreen,
                    layer = controlLayer,
                    videoOverlay = false,
                    callbacks = commonControls,
                    onInteraction = {},
                    onSeekingChanged = {},
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(ViewerIcons.Music, null, Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("播放队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${snapshot.queue.size} 首 · 点按歌曲即可切换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(snapshot.queue, key = { it.uri.toString() }) { item ->
                    val selected = item.uri == snapshot.current?.uri
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clickable { controller.open(snapshot.queue, item) },
                    ) {
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(item.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(if (selected && snapshot.state == PlaybackState.Playing) ViewerIcons.VolumeOn else ViewerIcons.Music, null) },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                }
                PlaybackStateOverlay(snapshot, false, Modifier.fillMaxSize().padding(padding), onBack, controller::retry)
            }
        }
    }
}

private data class PlayerControlCallbacks(
    val onSeek: (Long) -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onBackFive: () -> Unit,
    val onForwardFive: () -> Unit,
    val onMute: () -> Unit,
    val onVolume: (Int) -> Unit,
    val onRate: (Float) -> Unit,
    val onOrder: (PlaybackOrder) -> Unit,
    val onFullscreen: () -> Unit,
)

@Composable
private fun VideoTitleOverlay(
    title: String,
    state: PlaybackState,
    advanced: Boolean,
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .76f), Color.Black.copy(alpha = .38f), Color.Transparent),
                ),
            ),
        color = Color.Transparent,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(ViewerIcons.Back, "返回") }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(playbackLabel(state), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .78f))
            }
            IconButton(onClick = onAdvanced) {
                Icon(ViewerIcons.Tune, if (advanced) "关闭播放设置" else "打开播放设置")
            }
        }
    }
}

@Composable
private fun PlayerBottomLayer(
    snapshot: PlaybackSnapshot,
    settings: AppSettings,
    fullscreen: Boolean,
    layer: PlayerControlLayer,
    videoOverlay: Boolean,
    callbacks: PlayerControlCallbacks,
    onInteraction: () -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
) {
    val modifier = if (videoOverlay) {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .48f), Color.Black.copy(alpha = .82f)),
                ),
            )
            .padding(top = 24.dp)
            .navigationBarsPadding()
    } else {
        Modifier.fillMaxWidth().navigationBarsPadding()
    }
    Surface(
        modifier = modifier,
        color = if (videoOverlay) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = if (videoOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (videoOverlay) 0.dp else 8.dp,
    ) {
        when (layer) {
            PlayerControlLayer.Transport -> TransportControls(
                position = snapshot.positionMs,
                duration = snapshot.durationMs,
                playing = snapshot.state == PlaybackState.Playing,
                state = snapshot.state,
                canSeek = snapshot.canSeek,
                hasMultipleItems = snapshot.queue.size > 1,
                order = settings.playbackOrder,
                fullscreen = fullscreen,
                videoOverlay = videoOverlay,
                callbacks = callbacks,
                onInteraction = onInteraction,
                onSeekingChanged = onSeekingChanged,
            )
            PlayerControlLayer.Advanced -> AdvancedControls(
                muted = settings.muted,
                volume = settings.volume,
                rate = settings.playbackRate,
                enabled = snapshot.state != PlaybackState.Error,
                videoOverlay = videoOverlay,
                callbacks = callbacks,
                onInteraction = onInteraction,
            )
        }
    }
}

@Composable
private fun TransportControls(
    position: Long,
    duration: Long,
    playing: Boolean,
    state: PlaybackState,
    canSeek: Boolean,
    hasMultipleItems: Boolean,
    order: PlaybackOrder,
    fullscreen: Boolean,
    videoOverlay: Boolean,
    callbacks: PlayerControlCallbacks,
    onInteraction: () -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
) {
    var seeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(position.toFloat()) }
    val sliderColors = if (videoOverlay) {
        SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = .34f),
            disabledThumbColor = Color.White.copy(alpha = .45f),
            disabledActiveTrackColor = Color.White.copy(alpha = .35f),
            disabledInactiveTrackColor = Color.White.copy(alpha = .18f),
        )
    } else SliderDefaults.colors()
    val progressThumbColor = when {
        videoOverlay && canSeek -> Color.White
        videoOverlay -> Color.White.copy(alpha = .45f)
        canSeek -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
    }

    LaunchedEffect(position, duration) {
        if (!seeking) seekPosition = position.coerceIn(0, duration.coerceAtLeast(0)).toFloat()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(if (seeking) seekPosition.toLong() else position), style = MaterialTheme.typography.labelMedium)
            ViewerSlider(
                value = if (duration > 0) seekPosition.coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = {
                    if (!seeking) {
                        seeking = true
                        onSeekingChanged(true)
                    }
                    seekPosition = it
                    onInteraction()
                },
                onValueChangeFinished = {
                    callbacks.onSeek(seekPosition.toLong())
                    seeking = false
                    onSeekingChanged(false)
                    onInteraction()
                },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = canSeek,
                colors = sliderColors,
                thumbColor = progressThumbColor,
                modifier = Modifier.weight(1f).padding(horizontal = 7.dp),
            )
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderMenu(order, callbacks.onOrder, onInteraction)
            IconButton(onClick = { onInteraction(); callbacks.onPrevious() }, enabled = hasMultipleItems) {
                Icon(ViewerIcons.Previous, "上一项")
            }
            IconButton(onClick = { onInteraction(); callbacks.onBackFive() }, enabled = canSeek) {
                Icon(ViewerIcons.ReplayFive, "后退5秒")
            }
            FilledIconButton(
                onClick = { onInteraction(); callbacks.onPlayPause() },
                modifier = Modifier.size(52.dp),
                enabled = state !in setOf(PlaybackState.Opening, PlaybackState.Error, PlaybackState.Idle),
            ) {
                Icon(if (playing) ViewerIcons.Pause else ViewerIcons.Play, "播放或暂停")
            }
            IconButton(onClick = { onInteraction(); callbacks.onForwardFive() }, enabled = canSeek) {
                Icon(ViewerIcons.ForwardFive, "前进5秒")
            }
            IconButton(onClick = { onInteraction(); callbacks.onNext() }, enabled = hasMultipleItems) {
                Icon(ViewerIcons.Next, "下一项")
            }
            if (videoOverlay) {
                IconButton(onClick = { onInteraction(); callbacks.onFullscreen() }) {
                    Icon(if (fullscreen) ViewerIcons.FullscreenExit else ViewerIcons.Fullscreen, "全屏")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun AdvancedControls(
    muted: Boolean,
    volume: Int,
    rate: Float,
    enabled: Boolean,
    videoOverlay: Boolean,
    callbacks: PlayerControlCallbacks,
    onInteraction: () -> Unit,
) {
    var volumeDraft by remember { mutableFloatStateOf(volume.toFloat()) }
    LaunchedEffect(volume) { volumeDraft = volume.toFloat() }
    val sliderColors = if (videoOverlay) {
        SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = .34f),
        )
    } else SliderDefaults.colors()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onInteraction(); callbacks.onMute() }, enabled = enabled) {
                Icon(if (muted) ViewerIcons.VolumeOff else ViewerIcons.VolumeOn, if (muted) "解除静音" else "静音")
            }
            Text("音量", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            ViewerSlider(
                value = volumeDraft,
                onValueChange = { volumeDraft = it; onInteraction() },
                onValueChangeFinished = { callbacks.onVolume(volumeDraft.toInt()); onInteraction() },
                valueRange = 0f..100f,
                enabled = enabled && !muted,
                colors = sliderColors,
                thumbColor = if (videoOverlay) {
                    if (enabled && !muted) Color.White else Color.White.copy(alpha = .45f)
                } else {
                    null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${volumeDraft.toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(42.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("倍速", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                    val selected = rate == value
                    Surface(
                        modifier = Modifier.clickable(enabled = enabled) {
                            callbacks.onRate(value)
                            onInteraction()
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            selected && videoOverlay -> Color.White.copy(alpha = .24f)
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            videoOverlay -> Color.White.copy(alpha = .10f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            "${value}×",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackStateOverlay(
    snapshot: PlaybackSnapshot,
    video: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    when (snapshot.state) {
        PlaybackState.Opening -> Surface(
            modifier = modifier,
            color = if (video) Color.Black.copy(alpha = .74f) else MaterialTheme.colorScheme.surface.copy(alpha = .94f),
            contentColor = if (video) Color.White else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = if (video) Color.White else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(openingLabel(snapshot.openStage), fontWeight = FontWeight.SemiBold)
                if (snapshot.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.message, color = if (video) Color.White.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PlaybackState.Error -> Surface(
            modifier = modifier,
            color = if (video) Color.Black.copy(alpha = .86f) else MaterialTheme.colorScheme.surface,
            contentColor = if (video) Color.White else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("无法播放", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(snapshot.message.ifBlank { "这个媒体文件无法打开。" })
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) { Text("返回") }
                    if (snapshot.canRetry) Button(onClick = onRetry) { Text("重试") }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun OrderMenu(order: PlaybackOrder, onOrder: (PlaybackOrder) -> Unit, onInteraction: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onInteraction(); expanded = true }) {
            Icon(order.icon(), order.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlaybackOrder.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = { Icon(item.icon(), null) },
                    onClick = {
                        onOrder(item)
                        onInteraction()
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun PlaybackOrder.icon() = when (this) {
    PlaybackOrder.RepeatOne -> ViewerIcons.RepeatOne
    PlaybackOrder.Sequential -> ViewerIcons.Sequential
    PlaybackOrder.Shuffle -> ViewerIcons.Shuffle
}

private fun playbackLabel(state: PlaybackState) = when (state) {
    PlaybackState.Idle -> "准备播放"
    PlaybackState.Opening -> "正在加载"
    PlaybackState.Playing -> "正在播放"
    PlaybackState.Paused -> "已暂停"
    PlaybackState.Ended -> "播放完成"
    PlaybackState.Error -> "播放失败"
}

private fun openingLabel(stage: PlaybackOpenStage) = when (stage) {
    PlaybackOpenStage.None, PlaybackOpenStage.Preparing -> "正在准备媒体"
    PlaybackOpenStage.Accessing -> "正在读取本地文件"
    PlaybackOpenStage.RetryingSoftware -> "正在尝试兼容解码"
}

private fun formatTime(value: Long): String {
    val totalSeconds = (value.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
