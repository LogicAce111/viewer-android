package com.legion.viewer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PersistentMediaIndexTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory get() = File(temporary.root, "media-index")
    private fun store() = FileMediaIndexStore(directory)
    private fun sample(category: String = "Comics", root: String = "content://library/漫画", count: Int = 2) = SavedMediaIndex(
        IndexSource(category, root),
        (1..count).map {
            IndexedMedia("content://library/$it", "文档:$it", "作者/合集/第1本/$it.jpg", "$it.jpg", "作者/合集/第1本", 123456789L + it, 9876543210L + it)
        },
        ignoredCount = 3,
        skippedDirectories = 0,
    )

    @Test fun freshLoaderAfterRestartUsesSavedMetadataWithoutScanning() = runBlocking {
        val expected = sample()
        val first = PersistentIndexLoader(store()).load(expected.source, refresh = false) { expected }
        assertEquals(expected, first.index)
        val restarted = PersistentIndexLoader(store()).load(expected.source, refresh = false) {
            error("A process restart must not enumerate the directory")
        }
        assertEquals(expected, restarted.index)
    }

    @Test fun emptySuccessfulDirectoryIsPersisted() = runBlocking {
        val empty = sample(count = 0)
        PersistentIndexLoader(store()).load(empty.source, false) { empty }
        assertEquals(empty, PersistentIndexLoader(store()).load(empty.source, false) { error("Empty is a valid index") }.index)
    }

    @Test fun externalChangesAreIgnoredUntilManualRefresh() = runBlocking {
        val original = sample()
        val updated = sample(count = 4)
        store().write(original)
        var scans = 0
        val loader = PersistentIndexLoader(store())
        assertEquals(original, loader.load(original.source, false) { scans++; updated }.index)
        assertEquals(0, scans)
        var whileScanning: SavedMediaIndex? = null
        assertEquals(updated, loader.load(original.source, true, { whileScanning = it }) { scans++; updated }.index)
        assertEquals(original, whileScanning)
        assertEquals(1, scans)
        assertEquals(updated, store().read(original.source.category))
    }

    @Test fun changedDirectoryCannotReuseOldSource() = runBlocking {
        val original = sample()
        val changed = sample(root = "content://library/另一个目录", count = 1)
        store().write(original)
        var previous: SavedMediaIndex? = original
        val result = PersistentIndexLoader(store()).load(changed.source, false, { previous = it }) { changed }
        assertNull(previous)
        assertEquals(changed, result.index)
        assertEquals(changed, store().read("Comics"))
    }

    @Test fun clearAndReselectRequiresFreshImportOnlyForThatCategory() = runBlocking {
        listOf("Video", "Music", "Text", "Comics").forEach { store().write(sample(category = it)) }
        store().clear("Comics")
        assertNull(store().read("Comics"))
        listOf("Video", "Music", "Text").forEach { assertEquals(sample(category = it), store().read(it)) }
        var scans = 0
        PersistentIndexLoader(store()).load(sample().source, false) { scans++; sample() }
        assertEquals(1, scans)
    }

    @Test fun failedRefreshRetainsPersistedIndexAndAvailableContent() = runBlocking {
        val original = sample()
        store().write(original)
        val failure = expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(original.source, true) { throw IOException("授权失效") }
        }
        assertEquals(original, failure.previous)
        assertEquals(original, store().read("Comics"))
    }

    @Test fun failedNewSourceNeverOffersPreviousDirectory() = runBlocking {
        store().write(sample())
        val changed = sample(root = "content://library/new")
        val failure = expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(changed.source, false) { throw IOException("Missing directory") }
        }
        assertNull(failure.previous)
    }

    @Test fun partialScanCannotReplaceCompleteSavedIndex() = runBlocking {
        val original = sample()
        store().write(original)
        val failure = expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(original.source, true) { sample(count = 1).copy(skippedDirectories = 2) }
        }
        assertEquals(original, failure.previous)
        assertEquals(original, store().read("Comics"))
    }

    @Test fun firstPartialScanIsAvailableButNotSavedAsComplete() = runBlocking {
        val partial = sample(count = 1).copy(skippedDirectories = 1)
        val failure = expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(partial.source, false) { partial }
        }
        assertEquals(partial, failure.previous)
        assertNull(store().read("Comics"))
    }

    @Test fun corruptedIndexRequiresExplicitRefresh() = runBlocking {
        store().write(sample())
        val path = File(directory, "Comics.index")
        val bytes = path.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        path.writeBytes(bytes)
        var scans = 0
        expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(sample().source, false) { scans++; sample() }
        }
        assertEquals(0, scans)
        PersistentIndexLoader(store()).load(sample().source, true) { scans++; sample() }
        assertEquals(1, scans)
        assertEquals(sample(), store().read("Comics"))
    }

    @Test fun truncatedIndexIsAnErrorRatherThanAnEmptyDirectory() = runBlocking {
        store().write(sample())
        val file = File(directory, "Comics.index")
        file.writeBytes(file.readBytes().take(30).toByteArray())
        expectFailure<IOException> { store().read("Comics") }
        Unit
    }

    @Test fun saveFailureKeepsOldDiskDataAndExposesNewInMemoryResultWithWarning() = runBlocking {
        val original = sample()
        val updated = sample(count = 3)
        val disk = store()
        disk.write(original)
        val readOnly = object : MediaIndexStore by disk {
            override suspend fun write(index: SavedMediaIndex) { throw IOException("Disk full") }
        }
        val result = PersistentIndexLoader(readOnly).load(original.source, true) { updated }
        assertEquals(updated, result.index)
        assertNotNull(result.warning)
        assertEquals(original, disk.read("Comics"))
    }

    @Test fun cancelledScanDoesNotWriteOrConvertToFailure() = runBlocking {
        store().write(sample())
        expectFailure<CancellationException> {
            PersistentIndexLoader(store()).load(sample().source, true) { throw CancellationException("Replaced") }
        }
        assertEquals(sample(), store().read("Comics"))
    }

    @Test fun interruptedSnapshotWriteRetainsOriginalAndCleansTemporaryFile() = runBlocking {
        val original = sample()
        store().write(original)
        val interruptedItems = object : AbstractList<IndexedMedia>() {
            override val size = 2
            override fun get(index: Int): IndexedMedia {
                if (index == 1) throw CancellationException("Interrupted while serializing")
                return original.items.first()
            }
        }
        expectFailure<CancellationException> { store().write(original.copy(items = interruptedItems)) }
        assertEquals(original, store().read("Comics"))
        assertEquals(listOf("Comics.index"), directory.listFiles()!!.map { it.name })
    }

    @Test fun sourceMismatchFromScannerCannotBeCommitted() = runBlocking {
        store().write(sample())
        expectFailure<IndexLoadException> {
            PersistentIndexLoader(store()).load(sample().source, true) { sample(root = "content://wrong") }
        }
        assertEquals(sample(), store().read("Comics"))
    }

    @Test fun largeIndexPreservesAllEntriesAndTheirOrder() = runBlocking {
        val large = sample(count = 10000)
        store().write(large)
        assertEquals(large, store().read("Comics"))
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
