package com.legion.viewer.data

import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.MediaItem

internal data class ComicDirectoryGroup(val relativePath: String, val works: List<ComicWork>) {
    val name: String get() = when (relativePath) {
        "" -> "根目录"
        "根目录" -> "根目录（文件夹）"
        else -> relativePath
    }
}

/** An image's immediate directory remains one book; its parent is a collection. */
internal fun comicBookDirectory(imageRelativePath: String): String = imageRelativePath.substringBeforeLast('/', "")

internal fun buildComicWorks(items: List<MediaItem>): List<ComicWork> =
    items.groupBy { comicBookDirectory(it.relativePath) }
        .map { (directory, pages) ->
            ComicWork(directory.substringAfterLast('/').ifEmpty { "根目录" }, directory, pages.sortedWith(MediaNaturalComparator))
        }
        .sortedWith { left, right -> MediaNaturalComparator.natural(left.relativePath, right.relativePath) }

internal fun groupComicWorks(works: List<ComicWork>): List<ComicDirectoryGroup> =
    works.groupBy { it.relativePath.substringBeforeLast('/', "") }
        .map { (parent, books) ->
            ComicDirectoryGroup(parent, books.sortedWith { left, right -> MediaNaturalComparator.natural(left.relativePath, right.relativePath) })
        }
        .sortedWith { left, right ->
            when {
                left.relativePath.isEmpty() && right.relativePath.isNotEmpty() -> -1
                left.relativePath.isNotEmpty() && right.relativePath.isEmpty() -> 1
                else -> MediaNaturalComparator.natural(left.relativePath, right.relativePath)
            }
        }
