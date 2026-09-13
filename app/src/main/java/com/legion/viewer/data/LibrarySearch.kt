package com.legion.viewer.data

import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import kotlinx.coroutines.ensureActive
import java.util.Locale
import kotlin.coroutines.coroutineContext

private val queryWhitespace = Regex("[\\s\\p{Z}]+")
internal fun searchTerms(query: String): List<String> = query.lowercase(Locale.ROOT)
    .split(queryWhitespace).filter { it.isNotEmpty() }

internal data class SearchEntry<T>(val value: T, val name: String, val path: String)

/** Built from metadata only. Preserve source order and require every term to match. */
internal class NameDirectoryIndex<T>(entries: List<SearchEntry<T>>) {
    private val records = entries.map { it.value to "${it.name}\n${it.path}".lowercase(Locale.ROOT) }

    suspend fun search(terms: List<String>): List<T> = buildList {
        for ((value, text) in records) {
            coroutineContext.ensureActive()
            if (terms.all(text::contains)) add(value)
        }
    }
}

internal data class LibrarySearchResults(val items: List<MediaItem>, val comics: List<ComicWork>? = null) {
    val count: Int get() = comics?.size ?: items.size
}

internal class LibrarySearchIndex(category: MediaCategory, items: List<MediaItem>) {
    private val media = if (category != MediaCategory.Comics) {
        NameDirectoryIndex(items.map { SearchEntry(it, it.displayName, it.relativePath) })
    } else null
    private val comics = if (category == MediaCategory.Comics) comicSearchIndex(buildComicWorks(items)) else null

    suspend fun search(terms: List<String>): LibrarySearchResults {
        val works = comics?.search(terms)
        return if (works != null) LibrarySearchResults(works.flatMap { it.pages }, works)
        else LibrarySearchResults(requireNotNull(media).search(terms))
    }
}

internal fun comicSearchIndex(works: List<ComicWork>) =
    NameDirectoryIndex(works.map { SearchEntry(it, it.name, it.relativePath) })

/** The player owns a fixed snapshot, independent of later filtering or clearing the search. */
internal fun <T> snapshotPlaybackQueue(items: List<T>, selected: T): List<T>? =
    items.takeIf { selected in it }?.toList()
