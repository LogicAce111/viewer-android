package com.legion.viewer.ui

/** Shared, non-consuming tap classification for Compose and WebView readers. */
internal class ReaderTapGesture(
    private val x: Float,
    private val y: Float,
    private val startedAt: Long,
    private val width: Int,
    private val height: Int,
    private val touchSlop: Float,
    private val longPressTimeout: Long,
) {
    private var cancelled = false

    fun cancel() { cancelled = true }

    fun move(x: Float, y: Float, pointerCount: Int) {
        if (pointerCount != 1 || (x - this.x) * (x - this.x) + (y - this.y) * (y - this.y) > touchSlop * touchSlop) {
            cancelled = true
        }
    }

    fun finish(x: Float, y: Float, time: Long): Boolean {
        move(x, y, 1)
        return !cancelled && time - startedAt in 0 until longPressTimeout &&
            inContent(this.x, this.y) && inContent(x, y)
    }

    private fun inContent(x: Float, y: Float) = width > 0 && height > 0 &&
        x >= 0f && x < width && y >= 0f && y < height
}
