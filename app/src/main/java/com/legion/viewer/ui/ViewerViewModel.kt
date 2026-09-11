package com.legion.viewer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legion.viewer.ViewerApplication
import com.legion.viewer.data.AppContainer
import com.legion.viewer.data.IndexLoadException
import com.legion.viewer.data.IndexOperationQueue
import com.legion.viewer.data.IndexSource
import com.legion.viewer.data.indexSource
import com.legion.viewer.data.toSavedIndex
import com.legion.viewer.data.toScanResult
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.AppTheme
import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.CategorySource
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.PlaybackOrder
import com.legion.viewer.model.ReaderAppearance
import com.legion.viewer.model.ScanResult
import com.legion.viewer.model.availableContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface ViewerScreen {
    data object Home : ViewerScreen
    data object Settings : ViewerScreen
    data class Category(val category: MediaCategory) : ViewerScreen
    data object Player : ViewerScreen
    data class TextReader(val item: MediaItem) : ViewerScreen
    data class ComicReader(val work: ComicWork) : ViewerScreen
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    val container: AppContainer = (application as ViewerApplication).container
    val settings = container.preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )
    val playback = container.playback.snapshot

    private val _screen = MutableStateFlow<ViewerScreen>(ViewerScreen.Home)
    val screen: StateFlow<ViewerScreen> = _screen.asStateFlow()
    private val _scans = MutableStateFlow(MediaCategory.entries.associateWith<MediaCategory, ScanResult> { ScanResult.NotConfigured })
    val scans: StateFlow<Map<MediaCategory, ScanResult>> = _scans.asStateFlow()
    private val indexOperations = IndexOperationQueue(viewModelScope)
    private val loaded = mutableSetOf<MediaCategory>()
    private val contentSources = mutableMapOf<MediaCategory, IndexSource>()
    private val backStack = ArrayDeque<ViewerScreen>()

    fun navigate(target: ViewerScreen, rememberCurrent: Boolean = true) {
        if (rememberCurrent && _screen.value != target) backStack.addLast(_screen.value)
        _screen.value = target
        if (target is ViewerScreen.Category) ensureScanned(target.category)
    }

    fun navigateTopLevel(target: ViewerScreen) {
        backStack.clear()
        _screen.value = target
        if (target is ViewerScreen.Category) ensureScanned(target.category)
    }

    fun navigateUp() {
        val current = _screen.value
        if (current == ViewerScreen.Player && playback.value.isVideo) container.playback.pauseVideoForBackground()
        val target = when (current) {
            ViewerScreen.Home -> ViewerScreen.Home
            ViewerScreen.Settings, is ViewerScreen.Category -> ViewerScreen.Home
            ViewerScreen.Player -> playback.value.current?.category?.let { ViewerScreen.Category(it) } ?: ViewerScreen.Home
            is ViewerScreen.TextReader -> ViewerScreen.Category(MediaCategory.Text)
            is ViewerScreen.ComicReader -> ViewerScreen.Category(MediaCategory.Comics)
        }
        backStack.clear()
        _screen.value = target
        if (target is ViewerScreen.Category) ensureScanned(target.category)
    }

    fun back() = navigateUp()

    fun selectDirectory(category: MediaCategory, uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { container.log.error("persist-uri", it) }
        runIndexOperation(category) {
            // Invalidate before saving the source so even A -> B -> A requires a new import.
            container.mediaIndex.clear(category.name)
            contentSources.remove(category)
            _scans.update { it + (category to ScanResult.Loading()) }
            val name = withContext(Dispatchers.IO) { queryDirectoryName(uri) }
            container.preferences.setSource(category, uri, name)
            loadIndex(CategorySource(category, uri, name, enabled = true), refresh = true, retainPrevious = false)
        }
    }

    fun clearDirectory(category: MediaCategory) {
        runIndexOperation(category) {
            container.mediaIndex.clear(category.name)
            container.preferences.setSource(category, null)
            contentSources.remove(category)
            _scans.update { it + (category to ScanResult.NotConfigured) }
        }
    }

    fun ensureScanned(category: MediaCategory) {
        if (category in loaded || indexOperations.isActive(category.name)) return
        runIndexOperation(category) {
            _scans.update { it + (category to ScanResult.Loading(fromIndex = true)) }
            // Await DataStore instead of the temporary AppSettings() value from stateIn.
            val source = container.preferences.settings.first().sources.getValue(category)
            loadIndex(source, refresh = false, retainPrevious = false)
        }
    }

    fun refresh(category: MediaCategory) {
        runIndexOperation(category) {
            val source = container.preferences.settings.first().sources.getValue(category)
            loadIndex(source, refresh = true, retainPrevious = true)
        }
    }

    private fun runIndexOperation(category: MediaCategory, action: suspend () -> Unit) {
        indexOperations.replace(category.name) {
            try {
                action()
                coroutineContext.ensureActive()
                loaded += category
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                container.log.errorType("media-index", error)
                val previous = _scans.value[category]?.availableContent() ?: (error as? IndexLoadException)?.previous?.let { saved ->
                    withContext(Dispatchers.IO) { saved.toScanResult() }
                }
                _scans.update {
                    it + (category to ScanResult.Failure(
                        (error as? IndexLoadException)?.message ?: "目录索引操作失败，请手动刷新或重新选择目录。",
                        previous,
                    ))
                }
                // Do not turn page navigation into automatic retries/scans after an error.
                loaded += category
            }
        }
    }

    private suspend fun loadIndex(source: CategorySource, refresh: Boolean, retainPrevious: Boolean) {
        val category = source.category
        if (!source.enabled || source.treeUri == null) {
            contentSources.remove(category)
            _scans.update { it + (category to ScanResult.NotConfigured) }
            return
        }
        val request = source.indexSource()
        var previous = _scans.value[category]?.availableContent().takeIf {
            retainPrevious && contentSources[category] == request
        }
        contentSources[category] = request
        _scans.update { it + (category to ScanResult.Loading(previous = previous, fromIndex = true)) }
        val (content, warning) = withContext(Dispatchers.IO) {
            val loadedIndex = container.indexLoader.load(request, refresh, onScanning = { cached ->
                previous = previous ?: cached?.toScanResult()
                _scans.update { it + (category to ScanResult.Loading(previous = previous)) }
            }) {
                val scanContext = coroutineContext
                when (val scan = container.scanner.scan(source) { progress ->
                    scanContext.ensureActive()
                    _scans.update { it + (category to ScanResult.Loading(progress, previous)) }
                }) {
                    is ScanResult.Success -> scan.toSavedIndex(request)
                    is ScanResult.Failure -> throw IndexLoadException(scan.message)
                    else -> throw IndexLoadException("目录未配置，请重新选择目录。")
                }
            }
            loadedIndex.index.toScanResult() to loadedIndex.warning
        }
        coroutineContext.ensureActive()
        _scans.update { it + (category to if (warning == null) content else ScanResult.Failure(warning, content)) }
    }

    fun openMedia(item: MediaItem) {
        val result = scans.value[item.category]?.availableContent() ?: return
        when (item.category) {
            MediaCategory.Video, MediaCategory.Music -> {
                container.playback.open(result.items, item)
                navigate(ViewerScreen.Player)
            }
            MediaCategory.Text -> navigate(ViewerScreen.TextReader(item))
            MediaCategory.Comics -> Unit
        }
    }

    fun openComic(work: ComicWork) = navigate(ViewerScreen.ComicReader(work))

    fun toggleTheme() = viewModelScope.launch {
        container.preferences.setTheme(if (settings.value.appTheme == AppTheme.Dark) AppTheme.Light else AppTheme.Dark)
    }

    fun setReader(value: ReaderAppearance) = viewModelScope.launch { container.preferences.setReader(value) }
    fun setComicWidth(value: Float) = viewModelScope.launch { container.preferences.setComicWidth(value) }
    fun setPlaybackOrder(value: PlaybackOrder) = container.playback.setOrder(value)
    fun saveTextProgress(item: MediaItem, ratio: Float) = viewModelScope.launch {
        container.progress.saveText(item, ratio)
    }
    fun saveComicProgress(work: ComicWork, index: Int, offset: Int) = viewModelScope.launch {
        container.progress.saveComic(work.cover, index.coerceIn(work.pages.indices), offset)
    }

    private fun queryDirectoryName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        return runCatching {
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }
}
