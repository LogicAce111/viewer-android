package com.legion.viewer.data

import android.app.Application
import com.legion.viewer.playback.PlaybackController
import java.io.File

class AppContainer(application: Application) {
    val log = LocalLog(application)
    val preferences = PreferencesRepository(application)
    private val database = ViewerDatabase.create(application)
    val progress = ProgressRepository(database.progressDao())
    val scanner: MediaScanner = DocumentMediaScanner(application.contentResolver)
    val mediaIndex: MediaIndexStore = FileMediaIndexStore(File(application.filesDir, "media-index"))
    val indexLoader = PersistentIndexLoader(mediaIndex)
    val thumbnails = VideoThumbnailRepository(application, log)
    val playback = PlaybackController(application, preferences, progress, log)
}
