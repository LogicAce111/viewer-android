package com.legion.viewer.playback

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.ILibVLC
import java.io.Closeable

/** Keeps a SAF descriptor alive for as long as LibVLC is using the current media. */
class SafMediaSource private constructor(
    val uri: Uri,
    private val assetDescriptor: AssetFileDescriptor?,
    private val parcelDescriptor: ParcelFileDescriptor?,
) : Closeable {
    private var closed = false

    fun createMedia(libVlc: ILibVLC): Media {
        check(!closed) { "Media source is already closed" }
        return assetDescriptor?.let { Media(libVlc, it) }
            ?: parcelDescriptor?.let { Media(libVlc, it.fileDescriptor) }
            ?: error("Media source has no descriptor")
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { assetDescriptor?.close() }
        runCatching { parcelDescriptor?.close() }
    }

    companion object {
        fun open(resolver: ContentResolver, uri: Uri): SafMediaSource {
            val asset = runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
            if (asset != null) return SafMediaSource(uri, asset, null)

            val parcel = resolver.openFileDescriptor(uri, "r")
                ?: error("The document provider did not return a readable descriptor")
            return SafMediaSource(uri, null, parcel)
        }
    }
}
