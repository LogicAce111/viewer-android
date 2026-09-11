package com.legion.viewer.playback

import kotlin.math.abs

/** One historical seek per open request, confirmed only by a subsequent native time event. */
internal class PlaybackResumeAttempt(val requestId: Long, val positionMs: Long) {
    private var submittedAt: Long? = null
    private var consumed = false

    fun submitted(now: Long) { if (!consumed) submittedAt = now }
    fun cancel() { consumed = true }

    fun confirm(requestId: Long, positionMs: Long, now: Long): Boolean {
        val submitted = submittedAt ?: return false
        if (consumed || this.requestId != requestId || this.positionMs <= 0) return false
        if (now - submitted > 5_000) { consumed = true; return false }
        // VLC reports times at finite intervals, so accept up to one second of playback after seeking.
        if (positionMs <= 0 || abs(positionMs - this.positionMs) > 1_000) return false
        consumed = true
        return true
    }
}
