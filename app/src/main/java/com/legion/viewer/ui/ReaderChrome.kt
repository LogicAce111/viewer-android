package com.legion.viewer.ui

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

internal class ReaderChromeState {
    var visible by mutableStateOf(true)
        private set
    var interaction by mutableIntStateOf(0)
        private set
    var composePressed by mutableStateOf(false)
    var nativePressed by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var resumed by mutableStateOf(false)
    var autoHideBlocked by mutableStateOf(false)

    fun interact() { interaction++ }
    fun reveal() { visible = true; interact() }
    fun toggle() { if (ready) { visible = !visible; interact() } }
    fun hide() { visible = false }
}

@Composable
internal fun rememberReaderChrome(key: Any, ready: Boolean): ReaderChromeState {
    val state = remember(key) { ReaderChromeState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(state, ready) {
        state.ready = ready
        state.reveal()
    }
    DisposableEffect(state, lifecycleOwner, activity) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        state.resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { state.resumed = true; state.reveal() }
                Lifecycle.Event.ON_PAUSE -> {
                    state.resumed = false
                    state.composePressed = false
                    state.nativePressed = false
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
        }
    }
    LaunchedEffect(state, state.visible, state.resumed, activity) {
        activity?.let {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            if (state.visible || !state.resumed) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(state, state.visible, state.ready, state.resumed, state.interaction, state.composePressed, state.nativePressed, state.autoHideBlocked) {
        if (state.visible && state.ready && state.resumed && !state.composePressed && !state.nativePressed && !state.autoHideBlocked) {
            val lastInteraction = state.interaction
            delay(3_000)
            // A touch may arrive before recomposition cancels this effect.
            if (state.ready && state.resumed && !state.composePressed && !state.nativePressed && !state.autoHideBlocked && state.interaction == lastInteraction) {
                state.hide()
            }
        }
    }
    return state
}

/** Observe all touches, including controls, before children consume them. */
private fun Modifier.readerActivity(state: ReaderChromeState) = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.composePressed = true
        state.interact()
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                state.interact()
            } while (event.changes.any { it.pressed })
        } finally {
            state.composePressed = false
            state.interact()
        }
    }
}

/** Applied to content only; never consumes events needed by scrolling children. */
internal fun Modifier.readerContentTap(state: ReaderChromeState) = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val gesture = ReaderTapGesture(down.position.x, down.position.y, down.uptimeMillis,
            size.width, size.height, viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis)
        // A scrollbar can consume only DOWN; remember that even if UP is unconsumed.
        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) gesture.cancel()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            gesture.move(change.position.x, change.position.y, event.changes.size)
            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
            if (finalEvent.changes.any { it.isConsumed }) gesture.cancel()
            if (!change.pressed && gesture.finish(change.position.x, change.position.y, change.uptimeMillis)) {
                state.toggle()
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
internal fun ReaderFrame(
    state: ReaderChromeState,
    tools: ReaderToolsState,
    canReturnTop: Boolean,
    onReturnTop: () -> Unit,
    background: Color = MaterialTheme.colorScheme.background,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    // Content extends behind system bars and cutouts; only interactive chrome needs safe insets.
    // Keeping the content viewport fixed also avoids scroll jumps when bars appear/disappear.
    Box(Modifier.fillMaxSize().background(background).readerActivity(state)) {
        content()
        if (state.visible) {
            Box(
                Modifier.align(Alignment.TopCenter).readerOverlay()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) { Column { topBar(); ReaderUndoRow(tools, state.ready) } }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            ) {
                Column {
                    // The floating button and brief hint sit directly over the reading content.
                    ReaderToolsRow(tools, canReturnTop, onReturnTop)
                    Box(
                        Modifier.readerOverlay().background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                            .navigationBarsPadding(),
                    ) { bottomBar() }
                }
            }
        }
    }
}

/** Give blank toolbar areas their own hit target without consuming slider/button gestures. */
private fun Modifier.readerOverlay() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Native dispatch observation preserves WebView scrolling, selection, and cancellation. */
@SuppressLint("ViewConstructor") // Created only by AndroidView, never inflated from XML.
internal class ReaderWebView(context: Context, private val chrome: ReaderChromeState) : WebView(context) {
    private var gesture: ReaderTapGesture? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        chrome.interact()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                chrome.nativePressed = true
                gesture = ReaderTapGesture(event.x, event.y, event.eventTime, width, height,
                    ViewConfiguration.get(context).scaledTouchSlop.toFloat(), ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> gesture?.move(event.x, event.y, event.pointerCount)
            MotionEvent.ACTION_CANCEL -> { gesture = null; chrome.nativePressed = false }
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (gesture?.finish(event.x, event.y, event.eventTime) == true) chrome.toggle()
            gesture = null
            chrome.nativePressed = false
        }
        return result
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
