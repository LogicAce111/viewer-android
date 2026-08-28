package com.legion.viewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import com.legion.viewer.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

data class VideoThumbnailData(
    val bitmap: Bitmap,
    val durationMs: Long,
)

class VideoThumbnailRepository(
    private val context: Context,
    private val log: LocalLog,
) {
    private val directory = File(context.cacheDir, "video-thumbnails").apply { mkdirs() }
    private val permits = Semaphore(4)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { trimCache() }
    }

    suspend fun load(item: MediaItem, width: Int, height: Int): VideoThumbnailData? = withContext(Dispatchers.IO) {
        permits.withPermit {
            val key = cacheKey(item)
            val cached = File(directory, "$key.jpg")
            val metadata = File(directory, "$key.meta")
            if (cached.isFile) {
                BitmapFactory.decodeFile(cached.absolutePath)?.let {
                    cached.setLastModified(System.currentTimeMillis())
                    metadata.setLastModified(System.currentTimeMillis())
                    val durationMs = metadata.takeIf(File::isFile)
                        ?.let { runCatching { it.readText().trim().toLongOrNull() }.getOrNull() }
                        ?: readDuration(item)
                    if (!metadata.isFile) runCatching { writeMetadata(metadata, durationMs) }
                    return@withPermit VideoThumbnailData(it, durationMs)
                }
                cached.delete()
                metadata.delete()
            }

            val thumbnail = runCatching { createThumbnail(item, width, height) }
                .onFailure { log.errorType("video-thumbnail-${item.category.name}", it) }
                .getOrNull() ?: return@withPermit null
            coroutineContext.ensureActive()

            runCatching {
                val temporary = File(directory, "${cached.name}.tmp-${System.nanoTime()}")
                temporary.outputStream().buffered().use { output ->
                    thumbnail.bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                }
                if (!temporary.renameTo(cached)) {
                    temporary.copyTo(cached, overwrite = true)
                    temporary.delete()
                }
                writeMetadata(metadata, thumbnail.durationMs)
                trimCache()
            }.onFailure { log.errorType("video-thumbnail-cache-${item.category.name}", it) }
            thumbnail
        }
    }

    private fun createThumbnail(item: MediaItem, width: Int, height: Int): VideoThumbnailData {
        var durationMs = 0L
        var retrieverFailure: Throwable? = null
        val frame = runCatching {
            withRetriever(item) {
                    durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.coerceAtLeast(0L)
                        ?: 0L
                    val timeUs = durationMs.coerceAtLeast(0L) * 1_000L / 2L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        val (scaledWidth, scaledHeight) = scaledFrameSize(this, width, height)
                        getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            scaledWidth,
                            scaledHeight,
                        )
                    } else {
                        getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } ?: error("Video frame is unavailable")
            }
        }.onFailure { retrieverFailure = it }.getOrNull()

        if (frame != null) return VideoThumbnailData(frame, durationMs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val systemThumbnail = runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(width, height), null)
            }.getOrNull()
            if (systemThumbnail != null) return VideoThumbnailData(systemThumbnail, durationMs)
        }

        throw retrieverFailure ?: IllegalStateException("Video thumbnail is unavailable")
    }

    private fun scaledFrameSize(
        retriever: MediaMetadataRetriever,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Pair<Int, Int> {
        var sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: requestedWidth
        var sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: requestedHeight
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        if (rotation % 180 != 0) {
            val originalWidth = sourceWidth
            sourceWidth = sourceHeight
            sourceHeight = originalWidth
        }

        val scale = max(
            requestedWidth.toFloat() / sourceWidth,
            requestedHeight.toFloat() / sourceHeight,
        ).coerceAtMost(1f)
        return (sourceWidth * scale).toInt().coerceAtLeast(1) to
            (sourceHeight * scale).toInt().coerceAtLeast(1)
    }

    private fun readDuration(item: MediaItem): Long = runCatching {
        withRetriever(item) {
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        }
    }.getOrDefault(0L)

    private fun <T> withRetriever(item: MediaItem, block: MediaMetadataRetriever.() -> T): T {
        val assetDescriptor = runCatching {
            context.contentResolver.openAssetFileDescriptor(item.uri, "r")
        }.getOrNull()
        if (assetDescriptor != null) {
            assetDescriptor.use { descriptor ->
                return MediaMetadataRetriever().run {
                    try {
                        if (descriptor.declaredLength >= 0L) {
                            setDataSource(
                                descriptor.fileDescriptor,
                                descriptor.startOffset,
                                descriptor.declaredLength,
                            )
                        } else {
                            setDataSource(descriptor.fileDescriptor)
                        }
                        block()
                    } finally {
                        release()
                    }
                }
            }
        }

        val fileDescriptor = context.contentResolver.openFileDescriptor(item.uri, "r")
            ?: error("Video descriptor is unavailable")
        fileDescriptor.use { descriptor ->
            return MediaMetadataRetriever().run {
                try {
                    setDataSource(descriptor.fileDescriptor)
                    block()
                } finally {
                    release()
                }
            }
        }
    }

    private fun writeMetadata(file: File, durationMs: Long) {
        val temporary = File(directory, "${file.name}.tmp-${System.nanoTime()}")
        temporary.writeText(durationMs.toString())
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun cacheKey(item: MediaItem): String {
        val value = "${item.uri}|${item.modifiedAt}|${item.size}"
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun trimCache() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.contains(".tmp-") } ?: return
        var size = files.sumOf { it.length() }
        if (size <= MAX_CACHE_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            val length = file.length()
            if (file.delete()) size -= length
            if (size <= MAX_CACHE_BYTES) break
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    }
}
