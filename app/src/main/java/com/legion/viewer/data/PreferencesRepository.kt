package com.legion.viewer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.AppTheme
import com.legion.viewer.model.CategorySource
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.PlaybackOrder
import com.legion.viewer.model.ReaderAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.viewerDataStore by preferencesDataStore("viewer_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("app_theme")
        val volume = intPreferencesKey("volume")
        val muted = booleanPreferencesKey("muted")
        val rate = floatPreferencesKey("playback_rate")
        val order = stringPreferencesKey("playback_order")
        val fontSize = floatPreferencesKey("reader_font_size")
        val lineHeight = floatPreferencesKey("reader_line_height")
        val contentWidth = floatPreferencesKey("reader_content_width")
        val readerDark = booleanPreferencesKey("reader_dark")
        val comicWidth = floatPreferencesKey("comic_width")
        fun sourceUri(category: MediaCategory) = stringPreferencesKey("source_${category.name}_uri")
        fun sourceName(category: MediaCategory) = stringPreferencesKey("source_${category.name}_name")
    }

    val settings: Flow<AppSettings> = context.viewerDataStore.data.map(::toSettings)

    private fun toSettings(prefs: Preferences): AppSettings {
        val sources = MediaCategory.entries.associateWith { category ->
            val rawUri = prefs[Keys.sourceUri(category)].orEmpty()
            CategorySource(
                category = category,
                treeUri = rawUri.takeIf(String::isNotBlank)?.let(Uri::parse),
                displayName = prefs[Keys.sourceName(category)].orEmpty(),
                enabled = rawUri.isNotBlank(),
            )
        }
        return AppSettings(
            sources = sources,
            appTheme = prefs[Keys.theme]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.Dark,
            volume = prefs[Keys.volume] ?: 80,
            muted = prefs[Keys.muted] ?: false,
            playbackRate = prefs[Keys.rate] ?: 1f,
            playbackOrder = prefs[Keys.order]?.let { runCatching { PlaybackOrder.valueOf(it) }.getOrNull() }
                ?: PlaybackOrder.Sequential,
            reader = ReaderAppearance(
                fontSize = prefs[Keys.fontSize] ?: 19f,
                lineHeight = prefs[Keys.lineHeight] ?: 1.8f,
                contentWidth = prefs[Keys.contentWidth] ?: 0.88f,
                dark = prefs[Keys.readerDark] ?: false,
            ),
            comicWidth = prefs[Keys.comicWidth] ?: 1f,
        )
    }

    suspend fun setSource(category: MediaCategory, uri: Uri?, displayName: String = "") {
        context.viewerDataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(Keys.sourceUri(category))
                prefs.remove(Keys.sourceName(category))
            } else {
                prefs[Keys.sourceUri(category)] = uri.toString()
                prefs[Keys.sourceName(category)] = displayName
            }
        }
    }

    suspend fun setTheme(theme: AppTheme) = edit { it[Keys.theme] = theme.name }
    suspend fun setVolume(value: Int) = edit { it[Keys.volume] = value.coerceIn(0, 100) }
    suspend fun setMuted(value: Boolean) = edit { it[Keys.muted] = value }
    suspend fun setRate(value: Float) = edit { it[Keys.rate] = value }
    suspend fun setOrder(value: PlaybackOrder) = edit { it[Keys.order] = value.name }
    suspend fun setComicWidth(value: Float) = edit { it[Keys.comicWidth] = value.coerceIn(.6f, 1f) }

    suspend fun setReader(value: ReaderAppearance) = edit {
        it[Keys.fontSize] = value.fontSize
        it[Keys.lineHeight] = value.lineHeight
        it[Keys.contentWidth] = value.contentWidth
        it[Keys.readerDark] = value.dark
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.viewerDataStore.edit(block)
    }
}

