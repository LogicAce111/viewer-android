package com.legion.viewer.ui

import com.legion.viewer.data.LibrarySearchResults
import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class CategorySearchStateTest {
    private fun result(name: String) = LibrarySearchResults(emptyList(), listOf(ComicWork(name, name, emptyList())))

    @Test fun rapidInputDebouncesToLatestQuery() = runBlocking {
        val calls = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 20) { _, terms ->
            calls += terms.single()
            done.complete(Unit)
            result(terms.single())
        }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("a")
        state.updateQuery("ab")
        state.updateQuery("abc")
        done.await()
        yield()
        assertEquals(listOf("abc"), calls)
        assertEquals("abc", state.results!!.comics!!.single().name)
    }

    @Test fun slowOldRequestCannotOverwriteNewResults() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val newFinished = CompletableDeferred<Unit>()
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, terms ->
            if (terms.single() == "old") {
                oldStarted.complete(Unit)
                withContext(NonCancellable) { releaseOld.await() }
            } else newFinished.complete(Unit)
            result(terms.single())
        }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("old")
        oldStarted.await()
        state.updateQuery("new")
        newFinished.await()
        yield()
        releaseOld.complete(Unit)
        yield()
        assertEquals("new", state.results!!.comics!!.single().name)
    }

    @Test fun unchangedContentAndReentryPreserveResultsBrowseAndDoNotRequestKeyboard() = runBlocking {
        var calls = 0
        val done = CompletableDeferred<Unit>()
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, _ ->
            calls++
            done.complete(Unit)
            result("book")
        }
        val content = emptyList<MediaItem>()
        state.updateContent(content)
        state.open()
        assertTrue(state.focusRequested)
        state.focusHandled()
        state.updateQuery("book")
        done.await()
        yield()
        val browse = state.browse
        browse.toggle("collection")
        state.updateContent(content)
        state.updateQuery(" BOOK ")
        assertSame(browse, state.browse)
        assertFalse(state.browse.isExpanded("collection"))
        assertFalse(state.focusRequested)
        assertEquals(1, calls)
    }

    @Test fun changedQueryResetsSearchBrowseButRefreshKeepsIt() = runBlocking {
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, terms -> result(terms.single()) }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("one")
        yield()
        val first = state.browse
        first.toggle("collection")
        state.updateContent(ArrayList())
        yield()
        assertSame(first, state.browse)
        state.updateQuery("two")
        yield()
        assertNotSame(first, state.browse)
        assertTrue(state.browse.isExpanded("collection"))
    }

    @Test fun closingCancelsPendingSearchAndClearsOnlySearchState() = runBlocking {
        var calls = 0
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 20) { _, _ -> calls++; result("book") }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("book")
        state.close()
        delay(35)
        assertEquals(0, calls)
        assertFalse(state.isOpen)
        assertFalse(state.isFiltering)
        assertFalse(state.isSearching)
        assertFalse(state.focusRequested)
        assertEquals("", state.query)
        assertNull(state.results)
    }

    @Test fun changingDirectoryDiscardsResultsEvenWhenOldWorkFinishesLater() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, _ ->
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            result("old directory")
        }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("old")
        started.await()
        state.resetSource()
        state.updateContent(ArrayList())
        release.complete(Unit)
        yield()
        assertFalse(state.isOpen)
        assertNull(state.results)
        assertEquals("", state.query)
    }

    @Test fun inputBeforeIndexLoadsSearchesWhenContentArrives() = runBlocking {
        val done = CompletableDeferred<Unit>()
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, _ -> done.complete(Unit); result("book") }
        state.open()
        state.updateQuery("book")
        assertFalse(state.isSearching)
        state.updateContent(emptyList())
        done.await()
        yield()
        assertEquals(1, state.results!!.count)
    }

    @Test fun modulesKeepIndependentQueriesAndBrowsingStates() = runBlocking {
        val comics = CategorySearchState(MediaCategory.Comics, this)
        val music = CategorySearchState(MediaCategory.Music, this)
        comics.open()
        comics.updateQuery("合集")
        music.open()
        music.updateQuery("专辑")
        comics.close()
        assertTrue(music.isOpen)
        assertEquals("专辑", music.query)
        assertNotSame(comics.browse, music.browse)
    }

    @Test fun failedSearchCanRetryWithoutDroppingQueryOrBrowse() = runBlocking {
        var attempts = 0
        val state = CategorySearchState(MediaCategory.Comics, this, Dispatchers.Unconfined, 0) { _, _ ->
            if (++attempts == 1) error("temporary error")
            result("book")
        }
        state.updateContent(emptyList())
        state.open()
        state.updateQuery("book")
        yield()
        val browse = state.browse
        assertNotNull(state.error)
        state.retry()
        yield()
        assertNull(state.error)
        assertEquals(1, state.results!!.count)
        assertSame(browse, state.browse)
        assertEquals("book", state.query)
    }
}
