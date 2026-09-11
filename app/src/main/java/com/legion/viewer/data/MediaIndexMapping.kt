package com.legion.viewer.data

import androidx.core.net.toUri
import com.legion.viewer.model.CategorySource
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.ScanResult

fun CategorySource.indexSource() = IndexSource(category.name, requireNotNull(treeUri).toString())

fun ScanResult.Success.toSavedIndex(source: IndexSource) = SavedMediaIndex(
    source,
    items.map {
        IndexedMedia(it.uri.toString(), it.documentId, it.relativePath, it.displayName, it.group, it.modifiedAt, it.size)
    },
    ignoredCount,
    skippedDirectories,
)

fun SavedMediaIndex.toScanResult(): ScanResult.Success {
    val category = MediaCategory.valueOf(source.category)
    return ScanResult.Success(
        items.map {
            MediaItem(it.uri.toUri(), it.documentId, it.relativePath, it.displayName, category, it.group, it.modifiedAt, it.size)
        },
        ignoredCount,
        skippedDirectories,
    )
}
