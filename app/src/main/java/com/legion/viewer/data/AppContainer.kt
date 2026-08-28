package com.legion.viewer.data

import android.app.Application
import com.legion.viewer.playback.PlaybackController

class AppContainer(application: Application) {
    val log = LocalLog(application)
    val preferences = PreferencesRepository(application)
    private val database = ViewerDatabase.create(application)
    val progress = ProgressRepository(database.progressDao())
    val scanner: MediaScanner = DocumentMediaScanner(application.contentResolver)
    val thumbnails = VideoThumbnailRepository(application, log)
    val playback = PlaybackController(application, preferences, progress, log)
}
