package com.legion.viewer.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackResumeAttemptTest {
    @Test fun readingHistoryAloneDoesNotProduceSuccess() {
        assertFalse(PlaybackResumeAttempt(1, 30_000).confirm(1, 30_000, 100))
    }

    @Test fun nativeTimeConfirmsOnlyTheMatchingOpenOnce() {
        val attempt = PlaybackResumeAttempt(1, 30_000)
        attempt.submitted(100)
        assertFalse(attempt.confirm(2, 30_000, 200))
        assertFalse(attempt.confirm(1, 0, 200))
        assertTrue(attempt.confirm(1, 30_300, 300))
        assertFalse(attempt.confirm(1, 30_400, 400))
    }

    @Test fun manualSeekOrFailureCancelsHistoryConfirmation() {
        val attempt = PlaybackResumeAttempt(1, 30_000)
        attempt.submitted(100)
        attempt.cancel()
        assertFalse(attempt.confirm(1, 30_000, 200))
    }

    @Test fun lateNaturalPlaybackDoesNotMasqueradeAsRestoration() {
        val attempt = PlaybackResumeAttempt(1, 30_000)
        attempt.submitted(100)
        assertFalse(attempt.confirm(1, 10_000, 200))
        assertFalse(attempt.confirm(1, 30_000, 5_101))
    }

    @Test fun decoderRetryDoesNotRepeatAlreadyConfirmedNotice() {
        val attempt = PlaybackResumeAttempt(1, 30_000)
        attempt.submitted(100)
        assertTrue(attempt.confirm(1, 30_000, 200))
        attempt.submitted(300)
        assertFalse(attempt.confirm(1, 30_000, 400))
    }

    @Test fun zeroPositionNeverProducesNotice() {
        val attempt = PlaybackResumeAttempt(1, 0)
        attempt.submitted(100)
        assertFalse(attempt.confirm(1, 100, 200))
    }
}
