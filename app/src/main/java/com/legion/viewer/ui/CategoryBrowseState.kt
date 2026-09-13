package com.legion.viewer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

internal data class BrowseGroup(val key: String, val items: List<String>)
internal data class BrowseEntry(val key: String, val content: Boolean = false)
internal data class BrowsePosition(val index: Int, val offset: Int)

/** Find the old visible item, or its nearest surviving neighbour (next before previous). */
internal fun restoreBrowsePosition(
    old: List<BrowseEntry>,
    new: List<BrowseEntry>,
    position: BrowsePosition,
): BrowsePosition {
    if (old.isEmpty() || new.isEmpty()) return BrowsePosition(0, 0)
    val indexes = new.mapIndexed { index, entry -> entry.key to index }.toMap()
    val start = position.index.coerceIn(old.indices)
    // The loading row is replaced by new content: never follow it to the end of the next batch.
    if (old[start].key == "load-more") return BrowsePosition(start.coerceAtMost(new.lastIndex), position.offset.coerceAtLeast(0))
    indexes[old[start].key]?.let { return BrowsePosition(it, position.offset.coerceAtLeast(0)) }
    for (distance in 1..old.size) {
        for (candidate in listOf(start + distance, start - distance)) {
            old.getOrNull(candidate)?.let { entry ->
                indexes[entry.key]?.let { return BrowsePosition(it, 0) }
            }
        }
    }
    return BrowsePosition(start.coerceAtMost(new.lastIndex), 0)
}

internal fun browseEntries(groups: List<BrowseGroup>, collapsed: Set<String>, limit: Int, summary: Boolean): List<BrowseEntry> {
    var remaining = limit
    var total = 0
    return buildList {
        if (summary) add(BrowseEntry("scan-summary"))
        groups.forEach { group ->
            add(BrowseEntry(group.key))
            if (group.key !in collapsed) {
                total += group.items.size
                val visible = group.items.take(remaining.coerceAtLeast(0))
                visible.forEach { add(BrowseEntry(it, content = true)) }
                remaining -= visible.size
            }
        }
        if (total > limit) add(BrowseEntry("load-more"))
    }
}

internal fun browseLimitForAnchor(entries: List<BrowseEntry>, position: BrowsePosition, currentLimit: Int): Int {
    val required = entries.take(position.index + 1).count { it.content }
    return maxOf(currentLimit, ((required + 59) / 60) * 60)
}

/** Session-only state, owned by the ViewModel; no Activity, media contents or disk persistence. */
internal class CategoryBrowseState {
    var limit by mutableIntStateOf(60)
        private set
    private val collapsed = mutableStateMapOf<String, Boolean>()
    private var list: LazyListState? = null
    private var grid: LazyGridState? = null
    private var entries = emptyList<BrowseEntry>()
    private var signature: LayoutSignature? = null

    fun isExpanded(groupKey: String): Boolean = collapsed[groupKey] != true

    fun toggle(groupKey: String) {
        if (collapsed[groupKey] == true) collapsed.remove(groupKey) else collapsed[groupKey] = true
    }

    fun loadMore(total: Int) {
        if (limit < total) limit = minOf(total, limit + 60)
    }

    /** Called only when the rendered data changes, before giving the Lazy layout its new items. */
    fun prepare(groups: List<BrowseGroup>, summary: Boolean) {
        val closed = collapsed.keys.toSet()
        val next = LayoutSignature(groups, closed, limit, summary)
        if (next == signature) return
        val oldPosition = list?.let { BrowsePosition(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
            ?: grid?.let { BrowsePosition(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
            ?: BrowsePosition(0, 0)
        val full = browseEntries(groups, closed, Int.MAX_VALUE, summary)
        val target = restoreBrowsePosition(entries, full, oldPosition)
        // New files before the anchor can push it past the previously loaded batch.
        val initial = browseEntries(groups, closed, limit, summary)
        if (full.getOrNull(target.index)?.key !in initial.map { it.key }.toSet()) {
            limit = browseLimitForAnchor(full, target, limit)
        }
        val visible = browseEntries(groups, closed, limit, summary)
        val restored = restoreBrowsePosition(entries, visible, oldPosition)
        if (restored != oldPosition || (entries != visible && entries.getOrNull(oldPosition.index)?.key == "load-more")) {
            list = list?.let { LazyListState(restored.index, restored.offset) }
            grid = grid?.let { LazyGridState(restored.index, restored.offset) }
        }
        entries = visible
        signature = next.copy(limit = limit)
    }

    fun listState(): LazyListState = list ?: LazyListState().also { list = it }
    fun gridState(): LazyGridState = grid ?: LazyGridState().also { grid = it }

    private data class LayoutSignature(val groups: List<BrowseGroup>, val collapsed: Set<String>, val limit: Int, val summary: Boolean)
}
