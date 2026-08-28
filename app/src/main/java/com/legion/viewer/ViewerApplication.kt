package com.legion.viewer

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.legion.viewer.data.AppContainer
import okio.Path.Companion.toPath

class ViewerApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            container.log.error("uncaught", error)
            systemHandler?.uncaughtException(thread, error)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("thumbnails").absolutePath.toPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .build()
}
