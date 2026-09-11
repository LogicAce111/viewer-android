package com.legion.viewer.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderToolsStateTest {
    @Test fun restoredNoticeIsShownOnlyOnceAndInteractionDoesNotExtendIt() {
        var now = 100L
        val chrome = ReaderChromeState()
        val tools = ReaderToolsState(chrome) { now }
        tools.showRestored()
        assertEquals(3_100, tools.restoreUntil)
        now = 2_000
        chrome.interact()
        tools.showRestored()
        assertEquals(3_100, tools.restoreUntil)
        now = 3_100
        tools.expireRestore()
        tools.showRestored()
        assertEquals(0, tools.restoreUntil)
    }

    @Test fun undoRestoresCapturedLocationOnceAndResumesAutoHide() = runBlocking {
        var location = 0
        val chrome = ReaderChromeState()
        val tools = ReaderToolsState(chrome) { 100L }
        tools.offerUndo { location = 42 }
        assertTrue(chrome.autoHideBlocked)
        tools.takeUndo()?.invoke()
        assertEquals(42, location)
        assertNull(tools.takeUndo())
        assertFalse(chrome.autoHideBlocked)
    }

    @Test fun expiryIsEnforcedEvenBeforeTimerCoroutineRuns() {
        var now = 100L
        val chrome = ReaderChromeState()
        val tools = ReaderToolsState(chrome) { now }
        tools.offerUndo { error("Expired undo must never execute") }
        now = 3_100
        assertNull(tools.takeUndo())
        assertNull(tools.undo)
        assertFalse(chrome.autoHideBlocked)
    }

    @Test fun manualHideDoesNotExtendUndoAndDoesNotPreventExpiry() {
        var now = 100L
        val chrome = ReaderChromeState().apply { ready = true }
        val tools = ReaderToolsState(chrome) { now }
        tools.offerUndo { }
        chrome.toggle()
        assertFalse(chrome.visible)
        now = 3_100
        tools.expireUndo()
        assertNull(tools.undo)
        assertFalse(chrome.autoHideBlocked)
    }

    @Test fun newReturnTopReplacesOldBookmarkAndGetsANewDeadline() = runBlocking {
        var now = 100L
        var location = 0
        val tools = ReaderToolsState(ReaderChromeState()) { now }
        tools.offerUndo { location = 10 }
        now = 2_000
        tools.offerUndo { location = 20 }
        now = 3_100
        tools.expireUndo()
        tools.takeUndo()?.invoke()
        assertEquals(20, location)
    }
}
