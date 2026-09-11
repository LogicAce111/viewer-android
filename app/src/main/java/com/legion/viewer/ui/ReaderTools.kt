package com.legion.viewer.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val READER_NOTICE_MS = 3_000L

internal class ReaderToolsState(
    private val chrome: ReaderChromeState,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    var restoreUntil by mutableLongStateOf(0L)
        private set
    private var restoreShown = false
    var undo by mutableStateOf<Undo?>(null)
        private set
    data class Undo(val expiresAt: Long, val restore: suspend () -> Unit)

    fun showRestored() {
        if (restoreShown) return
        restoreShown = true
        restoreUntil = clock() + READER_NOTICE_MS
        chrome.reveal()
    }

    fun expireRestore() { if (clock() >= restoreUntil) restoreUntil = 0 }

    fun offerUndo(restore: suspend () -> Unit) {
        undo = Undo(clock() + READER_NOTICE_MS, restore)
        chrome.autoHideBlocked = true
        chrome.reveal()
    }

    fun expireUndo() { if (undo?.let { clock() >= it.expiresAt } == true) clearUndo() }

    fun takeUndo(): (suspend () -> Unit)? {
        val action = undo?.takeIf { clock() < it.expiresAt }?.restore
        clearUndo()
        return action
    }

    private fun clearUndo() {
        undo = null
        chrome.autoHideBlocked = false
        chrome.interact()
    }
}

@Composable
internal fun rememberReaderTools(key: Any, chrome: ReaderChromeState): ReaderToolsState {
    val tools = remember(key, chrome) { ReaderToolsState(chrome) }
    LaunchedEffect(tools, tools.restoreUntil) {
        if (tools.restoreUntil > 0) {
            delay((tools.restoreUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            tools.expireRestore()
        }
    }
    LaunchedEffect(tools, tools.undo) {
        tools.undo?.let {
            delay((it.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            tools.expireUndo()
        }
    }
    return tools
}

@Composable
internal fun ResumePositionHint(reading: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = .92f), shape = MaterialTheme.shapes.small) {
        Text(if (reading) "已定位至上次阅读" else "已定位至上次播放",
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ReaderToolsRow(tools: ReaderToolsState, canReturnTop: Boolean, onReturnTop: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            if (tools.restoreUntil > 0) ResumePositionHint(reading = true)
        }
        FilledIconButton(onClick = onReturnTop, enabled = canReturnTop, shape = CircleShape, modifier = Modifier.size(48.dp)) {
            Icon(ViewerIcons.ReturnTop, "返回顶部")
        }
    }
}

@Composable
internal fun ReaderUndoRow(tools: ReaderToolsState, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    if (tools.undo != null) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已返回顶部", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = enabled, onClick = { tools.takeUndo()?.let { restore -> scope.launch { restore() } } },
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("撤销", color = Color(0xFFE53935))
                }
            }
        }
    }
}

internal data class ListReadingPosition(val index: Int, val offset: Int, val ratio: Float, val layoutKey: Any)

internal fun captureReadingPosition(state: LazyListState, count: Int, layoutKey: Any): ListReadingPosition {
    val index = state.firstVisibleItemIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    val offset = state.firstVisibleItemScrollOffset
    val height = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 1
    val fraction = (offset.toFloat() / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    return ListReadingPosition(index, offset, ((index + fraction) / count.coerceAtLeast(1)).coerceIn(0f, 1f), layoutKey)
}

internal suspend fun restoreReadingPosition(state: LazyListState, count: Int, saved: ListReadingPosition, layoutKey: Any) {
    if (count <= 0) return
    if (saved.layoutKey == layoutKey) state.scrollToItem(saved.index.coerceIn(0, count - 1), saved.offset)
    else scrollToReadingRatio(state, count, saved.ratio)
}

internal suspend fun scrollToReadingRatio(state: LazyListState, count: Int, ratio: Float) {
    if (count <= 0) return
    val raw = ratio.coerceIn(0f, 1f) * count
    val index = raw.toInt().coerceIn(0, count - 1)
    state.scrollToItem(index)
    val height = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
    if (height > 0) state.scrollToItem(index, (height * (raw - index).coerceIn(0f, 1f)).toInt())
}
