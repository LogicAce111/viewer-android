package com.legion.viewer.data

import android.content.ContentResolver
import android.database.Cursor
import android.provider.DocumentsContract
import com.legion.viewer.model.CategorySource
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.ScanProgress
import com.legion.viewer.model.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale
import kotlin.coroutines.coroutineContext

interface MediaScanner {
    suspend fun scan(source: CategorySource, onProgress: (ScanProgress) -> Unit): ScanResult
}

class DocumentMediaScanner(private val resolver: ContentResolver) : MediaScanner {
    override suspend fun scan(source: CategorySource, onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val treeUri = source.treeUri ?: return@withContext ScanResult.NotConfigured
        var checked = 0
        var ignored = 0
        var skipped = 0
        val result = mutableListOf<MediaItem>()
        val pending = ArrayDeque<DirectoryNode>()
        val visited = mutableSetOf<String>()

        try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            pending.add(DirectoryNode(rootId, "", isRoot = true))
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val directory = pending.removeFirst()
                if (!visited.add(directory.documentId)) continue
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
                val cursor = try {
                    resolver.query(childrenUri, PROJECTION, null, null, null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (security: SecurityException) {
                    if (directory.isRoot) throw security
                    skipped++
                    null
                } catch (_: Exception) {
                    skipped++
                    null
                }
                if (cursor == null) {
                    if (directory.isRoot) return@withContext ScanResult.Failure("无法读取所选目录，请重新选择目录。")
                    continue
                }

                try {
                    cursor.use { rows ->
                        while (rows.moveToNext()) {
                            coroutineContext.ensureActive()
                            val id = rows.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                            val name = rows.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME).orEmpty()
                            val mime = rows.text(DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                            if (name.isBlank() || name.startsWith('.')) continue
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                val childPath = if (directory.relativePath.isBlank()) name else "${directory.relativePath}/$name"
                                pending.addLast(DirectoryNode(id, childPath))
                                continue
                            }

                            checked++
                            if (checked % 25 == 0) onProgress(ScanProgress(checked, directory.relativePath))
                            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            if (extension !in source.category.formats) {
                                ignored++
                                continue
                            }
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            result += MediaItem(
                                uri = documentUri,
                                documentId = id,
                                relativePath = if (directory.relativePath.isBlank()) name else "${directory.relativePath}/$name",
                                displayName = name,
                                category = source.category,
                                group = directory.relativePath.ifBlank { "根目录" },
                                modifiedAt = rows.long(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                                size = rows.long(DocumentsContract.Document.COLUMN_SIZE),
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (security: SecurityException) {
                    if (directory.isRoot) throw security
                    skipped++
                } catch (_: Exception) {
                    skipped++
                }
            }
            onProgress(ScanProgress(checked, ""))
            ScanResult.Success(result.sortedWith(MediaNaturalComparator), ignored, skipped)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ScanResult.Failure("目录授权已失效，请重新选择目录。")
        } catch (error: Exception) {
            ScanResult.Failure(error.message ?: "无法读取所选目录。")
        }
    }

    private fun Cursor.text(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)
    private fun Cursor.long(column: String): Long = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L

    private data class DirectoryNode(val documentId: String, val relativePath: String, val isRoot: Boolean = false)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}

object MediaNaturalComparator : Comparator<MediaItem> {
    private val parts = Regex("(\\d+)|(\\D+)")

    override fun compare(left: MediaItem, right: MediaItem): Int = natural(left.relativePath, right.relativePath)

    fun natural(left: String, right: String): Int {
        val a = parts.findAll(left.lowercase(Locale.ROOT)).map { it.value }.toList()
        val b = parts.findAll(right.lowercase(Locale.ROOT)).map { it.value }.toList()
        for (index in 0 until minOf(a.size, b.size)) {
            val x = a[index]
            val y = b[index]
            val comparison = if (x.firstOrNull()?.isDigit() == true && y.firstOrNull()?.isDigit() == true) {
                compareNumericPart(x, y)
            } else {
                x.compareTo(y)
            }
            if (comparison != 0) return comparison
        }
        return a.size.compareTo(b.size).takeIf { it != 0 } ?: left.compareTo(right, ignoreCase = true)
    }

    private fun compareNumericPart(left: String, right: String): Int {
        val a = left.trimStart('0').ifEmpty { "0" }
        val b = right.trimStart('0').ifEmpty { "0" }
        return a.length.compareTo(b.length).takeIf { it != 0 }
            ?: a.compareTo(b).takeIf { it != 0 }
            ?: left.length.compareTo(right.length)
    }
}
