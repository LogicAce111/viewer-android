package com.legion.viewer.model

import android.net.Uri

enum class MediaCategory(
    val title: String,
    val formats: Set<String>,
) {
    Video("视频", setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v")),
    Music("音乐", setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma")),
    Text("文本", setOf("txt", "md")),
    Comics("漫画", setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")),
}

data class CategorySource(
    val category: MediaCategory,
    val treeUri: Uri? = null,
    val displayName: String = "",
    val enabled: Boolean = false,
)

data class MediaItem(
    val uri: Uri,
    val documentId: String,
    val relativePath: String,
    val displayName: String,
    val category: MediaCategory,
    val group: String,
    val modifiedAt: Long,
    val size: Long = 0,
) {
    val extension: String get() = displayName.substringAfterLast('.', "").lowercase()
    val title: String get() = displayName.substringBeforeLast('.', displayName)
}

data class ComicWork(
    val name: String,
    val relativePath: String,
    val pages: List<MediaItem>,
) {
    val cover: MediaItem get() = pages.first()
}

enum class PlaybackOrder(val label: String) {
    RepeatOne("单曲循环"),
    Sequential("顺序播放"),
    Shuffle("随机播放"),
}

enum class AppTheme { Dark, Light }

data class ReaderAppearance(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.8f,
    val contentWidth: Float = 0.88f,
    val dark: Boolean = false,
)

data class AppSettings(
    val sources: Map<MediaCategory, CategorySource> = MediaCategory.entries.associateWith { CategorySource(it) },
    val appTheme: AppTheme = AppTheme.Dark,
    val volume: Int = 80,
    val muted: Boolean = false,
    val playbackRate: Float = 1f,
    val playbackOrder: PlaybackOrder = PlaybackOrder.Sequential,
    val reader: ReaderAppearance = ReaderAppearance(),
    val comicWidth: Float = 1f,
)

data class ScanProgress(val checkedFiles: Int, val currentPath: String)

sealed interface ScanResult {
    data object NotConfigured : ScanResult
    data class Loading(
        val progress: ScanProgress = ScanProgress(0, ""),
        val previous: Success? = null,
    ) : ScanResult
    data class Success(
        val items: List<MediaItem>,
        val ignoredCount: Int,
        val skippedDirectories: Int,
    ) : ScanResult
    data class Failure(val message: String) : ScanResult
}

enum class PlaybackState { Idle, Opening, Playing, Paused, Ended, Error }

enum class PlaybackOpenStage { None, Accessing, Preparing, RetryingSoftware }

data class PlaybackSnapshot(
    val state: PlaybackState = PlaybackState.Idle,
    val current: MediaItem? = null,
    val queue: List<MediaItem> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isVideo: Boolean = false,
    val canSeek: Boolean = false,
    val canRetry: Boolean = false,
    val openStage: PlaybackOpenStage = PlaybackOpenStage.None,
    val message: String = "",
)
