package com.legion.viewer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legion.viewer.ViewerApplication
import com.legion.viewer.data.AppContainer
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.AppTheme
import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.CategorySource
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.PlaybackOrder
import com.legion.viewer.model.ReaderAppearance
import com.legion.viewer.model.ScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val scanJobs = mutableMapOf<MediaCategory, Job>()
    private val loaded = mutableSetOf<MediaCategory>()
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
        viewModelScope.launch {
            val name = queryDirectoryName(uri)
            container.preferences.setSource(category, uri, name)
            loaded.remove(category)
            startScan(CategorySource(category, uri, name, enabled = true), retainPrevious = false)
        }
    }

    fun clearDirectory(category: MediaCategory) {
        scanJobs.remove(category)?.cancel()
        viewModelScope.launch {
            container.preferences.setSource(category, null)
            loaded.remove(category)
            _scans.value = _scans.value + (category to ScanResult.NotConfigured)
        }
    }

    fun ensureScanned(category: MediaCategory) {
        if (category !in loaded) refresh(category)
    }

    fun refresh(category: MediaCategory) {
        val source = settings.value.sources.getValue(category)
        if (!source.enabled) {
            _scans.value = _scans.value + (category to ScanResult.NotConfigured)
            return
        }
        startScan(source, retainPrevious = true)
    }

    private fun startScan(source: CategorySource, retainPrevious: Boolean) {
        val category = source.category
        scanJobs.remove(category)?.cancel()
        val previous = (_scans.value[category] as? ScanResult.Success).takeIf { retainPrevious }
        scanJobs[category] = viewModelScope.launch {
            _scans.value = _scans.value + (category to ScanResult.Loading(previous = previous))
            val result = container.scanner.scan(source) { progress ->
                _scans.value = _scans.value + (category to ScanResult.Loading(progress, previous))
            }
            _scans.value = _scans.value + (category to result)
            if (result is ScanResult.Success) loaded += category
        }
    }

    fun openMedia(item: MediaItem) {
        val result = scans.value[item.category] as? ScanResult.Success ?: return
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
