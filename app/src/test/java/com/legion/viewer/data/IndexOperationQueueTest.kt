package com.legion.viewer.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class IndexOperationQueueTest {
    @Test fun replacementWaitsForOldProviderAndSkipsSupersededRequest() = runBlocking {
        val queue = IndexOperationQueue(this)
        val started = CompletableDeferred<Unit>()
        val providerReturned = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        queue.replace("Comics") {
            started.complete(Unit)
            withContext(NonCancellable) { providerReturned.await() }
            currentCoroutineContext().ensureActive()
            events += "old snapshot published"
        }
        started.await()
        queue.replace("Comics") { events += "intermediate source" }
        yield()
        val latest = queue.replace("Comics") { events += "latest source" }
        yield()
        assertTrue(events.isEmpty())
        providerReturned.complete(Unit)
        latest.join()
        assertEquals(listOf("latest source"), events)
        assertFalse(queue.isActive("Comics"))
    }

    @Test fun independentCategoriesDoNotBlockOneAnother() = runBlocking {
        val queue = IndexOperationQueue(this)
        val waiting = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val comics = queue.replace("Comics") { started.complete(Unit); waiting.await() }
        started.await()
        var musicLoaded = false
        queue.replace("Music") { musicLoaded = true }.join()
        assertTrue(musicLoaded)
        assertTrue(queue.isActive("Comics"))
        waiting.complete(Unit)
        comics.join()
    }

    @Test fun clearingRunsAfterCancelledWriterBeforeNewImport() = runBlocking {
        val queue = IndexOperationQueue(this)
        val writing = CompletableDeferred<Unit>()
        val writeCompleted = CompletableDeferred<Unit>()
        var persisted: String? = null
        queue.replace("Text") {
            writing.complete(Unit)
            withContext(NonCancellable) { writeCompleted.await(); persisted = "old source" }
        }
        writing.await()
        val clear = queue.replace("Text") { persisted = null }
        yield()
        writeCompleted.complete(Unit)
        clear.join()
        assertNull(persisted)
        queue.replace("Text") { persisted = "new source" }.join()
        assertEquals("new source", persisted)
    }
}
