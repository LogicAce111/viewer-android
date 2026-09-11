package com.legion.viewer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class IndexSource(val category: String, val treeUri: String)

/** Metadata only; media contents and reading/playback progress remain in their existing stores. */
data class IndexedMedia(
    val uri: String,
    val documentId: String,
    val relativePath: String,
    val displayName: String,
    val group: String,
    val modifiedAt: Long,
    val size: Long,
)

data class SavedMediaIndex(
    val source: IndexSource,
    val items: List<IndexedMedia>,
    val ignoredCount: Int,
    val skippedDirectories: Int,
)

interface MediaIndexStore {
    suspend fun read(category: String): SavedMediaIndex?
    suspend fun write(index: SavedMediaIndex)
    suspend fun clear(category: String)
}

/** Callers serialize operations for the same category, including source preference changes. */
class FileMediaIndexStore(private val directory: File) : MediaIndexStore {
    private fun target(category: String): File {
        require(category in setOf("Video", "Music", "Text", "Comics"))
        return File(directory, "$category.index")
    }

    override suspend fun read(category: String): SavedMediaIndex? = withContext(Dispatchers.IO) {
        val file = target(category)
        val input = try {
            Files.newInputStream(file.toPath())
        } catch (_: NoSuchFileException) {
            return@withContext null
        }
        input.use { raw ->
            val digest = MessageDigest.getInstance("SHA-256")
            val checked = DigestInputStream(raw.buffered(), digest)
            val data = DataInputStream(checked)
            if (data.readInt() != MAGIC || data.readInt() != VERSION) throw IOException("Unsupported index")
            val source = IndexSource(data.readText(), data.readText())
            if (source.category != category || source.treeUri.isBlank()) throw IOException("Invalid index source")
            val ignored = data.readNonNegativeInt()
            val skipped = data.readNonNegativeInt()
            val count = data.readNonNegativeInt()
            // Bound malformed lengths before allocating; valid large libraries are decoded incrementally.
            if (count > MAX_ITEMS) throw IOException("Invalid index count")
            val items = ArrayList<IndexedMedia>()
            repeat(count) {
                coroutineContext.ensureActive()
                items += IndexedMedia(
                    data.readText(), data.readText(), data.readText(), data.readText(), data.readText(),
                    data.readLong(), data.readLong(),
                )
            }
            checked.on(false)
            val checksum = ByteArray(32).also(data::readFully)
            if (!MessageDigest.isEqual(digest.digest(), checksum) || data.read() != -1) {
                throw IOException("Invalid index checksum")
            }
            SavedMediaIndex(source, items, ignored, skipped)
        }
    }

    override suspend fun write(index: SavedMediaIndex) = withContext(Dispatchers.IO) {
        val file = target(index.source.category)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create index directory")
        require(index.source.treeUri.isNotBlank() && index.items.size <= MAX_ITEMS)
        require(index.ignoredCount >= 0 && index.skippedDirectories >= 0)
        val temporary = File.createTempFile(file.name, ".tmp", directory)
        try {
            FileOutputStream(temporary).use { raw ->
                val digest = MessageDigest.getInstance("SHA-256")
                val checked = DigestOutputStream(raw.buffered(), digest)
                val data = DataOutputStream(checked)
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeText(index.source.category)
                data.writeText(index.source.treeUri)
                data.writeInt(index.ignoredCount)
                data.writeInt(index.skippedDirectories)
                data.writeInt(index.items.size)
                index.items.forEach { item ->
                    coroutineContext.ensureActive()
                    data.writeText(item.uri)
                    data.writeText(item.documentId)
                    data.writeText(item.relativePath)
                    data.writeText(item.displayName)
                    data.writeText(item.group)
                    data.writeLong(item.modifiedAt)
                    data.writeLong(item.size)
                }
                checked.on(false)
                data.write(digest.digest())
                data.flush()
                raw.fd.sync()
            }
            coroutineContext.ensureActive()
            // Same-directory atomic replacement keeps the last complete snapshot after interruption.
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Unit
        } finally {
            temporary.delete()
        }
    }

    override suspend fun clear(category: String) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(target(category).toPath())
        Unit
    }

    private fun DataInputStream.readNonNegativeInt(): Int = readInt().also {
        if (it < 0) throw IOException("Negative index count")
    }

    private fun DataInputStream.readText(): String {
        val size = readNonNegativeInt()
        if (size > MAX_STRING_BYTES) throw IOException("Invalid index string length")
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private companion object {
        const val MAGIC = 0x56495831
        const val VERSION = 1
        const val MAX_ITEMS = 1_000_000
        const val MAX_STRING_BYTES = 1_048_576
    }
}

class IndexLoadException(
    message: String,
    val previous: SavedMediaIndex? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

data class IndexLoadResult(val index: SavedMediaIndex, val warning: String? = null)

class PersistentIndexLoader(private val store: MediaIndexStore) {
    suspend fun load(
        source: IndexSource,
        refresh: Boolean,
        onScanning: (SavedMediaIndex?) -> Unit = {},
        scan: suspend () -> SavedMediaIndex,
    ): IndexLoadResult {
        val previous = try {
            store.read(source.category)?.takeIf { it.source == source }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!refresh) throw IndexLoadException("已保存的目录索引无法读取，请手动刷新。", cause = error)
            null
        }
        if (!refresh && previous != null) return IndexLoadResult(previous)
        coroutineContext.ensureActive()
        onScanning(previous)
        val scanned = try {
            scan().also {
                check(it.source == source) { "Index source changed during scan" }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IndexLoadException(error.message ?: "目录读取失败，请手动刷新或重新选择目录。", previous, error)
        }
        coroutineContext.ensureActive()
        if (scanned.skippedDirectories > 0) {
            throw IndexLoadException(
                "有 ${scanned.skippedDirectories} 个目录未能完整读取，未更新已保存的索引。请检查目录后手动刷新。",
                previous ?: scanned,
            )
        }
        return try {
            store.write(scanned)
            IndexLoadResult(scanned)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            IndexLoadResult(scanned, "本次内容已读取，但目录索引保存失败。请检查可用空间后手动刷新。")
        }
    }
}
