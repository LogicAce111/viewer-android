package com.legion.viewer.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalLog(context: Context) {
    private val directory = File(context.filesDir, "logs").apply { mkdirs() }
    private val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT)

    @Synchronized
    fun error(area: String, throwable: Throwable) {
        append(area, "${throwable.javaClass.simpleName}: ${throwable.message}")
    }

    @Synchronized
    fun errorType(area: String, throwable: Throwable) {
        append(area, throwable.javaClass.simpleName.ifBlank { "UnknownException" })
    }

    private fun append(area: String, detail: String) {
        runCatching {
            rotate()
            val target = File(directory, "viewer-${date.format(Date())}.log")
            target.appendText("${Date()} [$area] $detail\n")
        }
    }

    private fun rotate() {
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach(File::delete)
        directory.listFiles()?.filter { it.length() > 2L * 1024 * 1024 }?.forEach { file ->
            file.writeText(file.readText().takeLast(1024 * 1024))
        }
    }
}
