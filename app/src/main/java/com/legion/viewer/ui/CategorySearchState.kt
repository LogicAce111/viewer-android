package com.legion.viewer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.legion.viewer.data.LibrarySearchIndex
import com.legion.viewer.data.LibrarySearchResults
import com.legion.viewer.data.searchTerms
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** One module's in-session search; UI lifecycle changes never restart the query or request focus. */
internal class CategorySearchState(
    private val category: MediaCategory,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val debounceMs: Long = 200,
    private val searchOverride: (suspend (List<MediaItem>, List<String>) -> LibrarySearchResults)? = null,
) {
    var isOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var focusRequested by mutableStateOf(false)
        private set
    var isSearching by mutableStateOf(false)
        private set
    var results by mutableStateOf<LibrarySearchResults?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var browse by mutableStateOf(CategoryBrowseState())
        private set
    val isFiltering: Boolean get() = isOpen && searchTerms(query).isNotEmpty()

    private var source: List<MediaItem>? = null
    private var index: LibrarySearchIndex? = null
    private var indexedSource: List<MediaItem>? = null
    private var job: Job? = null
    private var generation = 0L

    fun open() {
        isOpen = true
        focusRequested = true
    }

    fun focusHandled() { focusRequested = false }

    fun updateQuery(value: String) {
        val changed = searchTerms(value) != searchTerms(query)
        query = value
        if (changed) {
            browse = CategoryBrowseState()
            schedule()
        }
    }

    fun updateContent(items: List<MediaItem>?) {
        if (items === source) return
        source = items
        index = null
        indexedSource = null
        schedule()
    }

    fun close() {
        isOpen = false
        focusRequested = false
        query = ""
        browse = CategoryBrowseState()
        schedule()
    }

    fun resetSource() {
        source = null
        index = null
        indexedSource = null
        close()
    }

    fun retry() = schedule()

    private fun schedule() {
        val request = ++generation
        job?.cancel()
        results = null
        error = null
        val items = source
        val terms = searchTerms(query)
        isSearching = isOpen && terms.isNotEmpty() && items != null
        if (!isSearching || items == null) return
        val cached = index.takeIf { indexedSource === items }
        job = scope.launch {
            try {
                delay(debounceMs)
                val (built, matches) = withContext(dispatcher) {
                    coroutineContext.ensureActive()
                    if (searchOverride != null) null to searchOverride.invoke(items, terms)
                    else {
                        val prepared = cached ?: LibrarySearchIndex(category, items)
                        prepared to prepared.search(terms)
                    }
                }
                if (generation != request) return@launch
                index = built
                indexedSource = items
                results = matches
                isSearching = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == request) {
                    isSearching = false
                    error = "搜索暂时失败，请重试。"
                }
            }
        }
    }
}
