package com.legion.viewer.ui

import org.junit.Assert.*
import org.junit.Test

class CategoryBrowseStateTest {
    private fun entries(vararg keys: String) = keys.map { BrowseEntry(it, true) }
    private fun group(size: Int) = BrowseGroup("group", (0 until size).map { "item:$it" })

    @Test fun unchangedLibraryKeepsExactItemOffset() {
        val before = entries("a", "b", "c")
        assertEquals(BrowsePosition(1, 73), restoreBrowsePosition(before, before, BrowsePosition(1, 73)))
    }

    @Test fun insertedItemsFollowStableKeyRatherThanOldIndex() {
        assertEquals(
            BrowsePosition(3, 42),
            restoreBrowsePosition(entries("a", "b", "c"), entries("new1", "new2", "a", "b", "c"), BrowsePosition(1, 42)),
        )
    }

    @Test fun removedAnchorUsesNearestSurvivingNeighbour() {
        val before = entries("a", "b", "c", "d")
        assertEquals(BrowsePosition(1, 0), restoreBrowsePosition(before, entries("a", "c", "d"), BrowsePosition(1, 50)))
        assertEquals(BrowsePosition(1, 0), restoreBrowsePosition(before, entries("a", "b"), BrowsePosition(3, 50)))
    }

    @Test fun removedEntireLibraryResetsOrClampsSafely() {
        assertEquals(BrowsePosition(0, 0), restoreBrowsePosition(entries("a", "b"), emptyList(), BrowsePosition(1, 80)))
        assertEquals(BrowsePosition(0, 0), restoreBrowsePosition(entries("a", "b"), entries("new"), BrowsePosition(1, 80)))
    }

    @Test fun sameNamedComicsInDifferentCollectionsHaveDifferentAnchors() {
        val before = entries("comic-work:作者A/第1本", "comic-work:作者B/第1本")
        val after = entries("comic-work:作者B/第1本", "comic-work:作者A/第1本")
        assertEquals(BrowsePosition(0, 10), restoreBrowsePosition(before, after, BrowsePosition(1, 10)))
    }

    @Test fun flattenedEntriesMatchSummaryGroupsCollapseAndPaging() {
        val groups = listOf(BrowseGroup("g1", listOf("a", "b")), BrowseGroup("g2", listOf("c", "d")))
        assertEquals(listOf("scan-summary", "g1", "g2", "c", "load-more"), browseEntries(groups, setOf("g1"), 1, true).map { it.key })
        assertEquals(listOf("g1", "g2", "c", "d"), browseEntries(groups, setOf("g1"), 60, false).map { it.key })
    }

    @Test fun anchorPushedBeyondOldBatchExpandsLoadedCount() {
        val full = browseEntries(listOf(group(240)), emptySet(), Int.MAX_VALUE, false)
        assertEquals(180, browseLimitForAnchor(full, BrowsePosition(151, 5), 120))
        assertEquals(180, browseLimitForAnchor(full, BrowsePosition(2, 5), 180))
    }

    @Test fun loadingRowDoesNotSkipNewlyLoadedItems() {
        val before = browseEntries(listOf(group(180)), emptySet(), 60, false)
        val after = browseEntries(listOf(group(180)), emptySet(), 120, false)
        assertEquals(BrowsePosition(61, 0), restoreBrowsePosition(before, after, BrowsePosition(61, 0)))
        assertEquals("item:60", after[61].key)
    }

    @Test fun independentModuleStatesAndFreshSessionHaveIndependentFoldsAndLimits() {
        val music = CategoryBrowseState()
        val comics = CategoryBrowseState()
        music.toggle("group")
        music.loadMore(200)
        assertFalse(music.isExpanded("group"))
        assertEquals(120, music.limit)
        assertTrue(comics.isExpanded("group"))
        assertEquals(60, comics.limit)
        assertTrue(CategoryBrowseState().isExpanded("group"))
    }

    @Test fun listStateSurvivesPageReentryAndRefreshMovesAnchor() {
        val browse = CategoryBrowseState()
        val groups = listOf(group(200))
        browse.loadMore(200)
        browse.prepare(groups, false)
        val original = browse.listState()
        original.requestScrollToItem(95, 27)
        browse.prepare(groups, false)
        assertSame(original, browse.listState())
        val changed = listOf(BrowseGroup("group", listOf("new") + groups.first().items))
        browse.prepare(changed, false)
        assertEquals(96, browse.listState().firstVisibleItemIndex)
        assertEquals(27, browse.listState().firstVisibleItemScrollOffset)
        assertEquals(120, browse.limit)
    }

    @Test fun gridStateSurvivesReentryWithFoldedGroupsAndDeepPosition() {
        val browse = CategoryBrowseState()
        val groups = listOf(group(250), BrowseGroup("closed", listOf("last")))
        browse.toggle("closed")
        repeat(2) { browse.loadMore(250) }
        browse.prepare(groups, false)
        val original = browse.gridState()
        original.requestScrollToItem(155, 33)
        browse.prepare(groups, false)
        assertSame(original, browse.gridState())
        assertEquals(155, browse.gridState().firstVisibleItemIndex)
        assertEquals(33, browse.gridState().firstVisibleItemScrollOffset)
        assertFalse(browse.isExpanded("closed"))
        assertEquals(180, browse.limit)
    }

    @Test fun refreshLoadsEnoughItemsToKeepTheActualGridAnchor() {
        val browse = CategoryBrowseState()
        val groups = listOf(group(200))
        browse.prepare(groups, false)
        browse.gridState().requestScrollToItem(59, 9)
        browse.prepare(listOf(BrowseGroup("group", (0..80).map { "new:$it" } + groups.first().items)), false)
        assertEquals(140, browse.gridState().firstVisibleItemIndex)
        assertEquals(9, browse.gridState().firstVisibleItemScrollOffset)
        assertEquals(180, browse.limit)
    }
}
