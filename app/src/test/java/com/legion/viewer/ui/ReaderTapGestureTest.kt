package com.legion.viewer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapGestureTest {
    private fun gesture(x: Float = 150f, y: Float = 300f) =
        ReaderTapGesture(x, y, 1_000, 300, 600, 12f, 500)

    @Test fun centerTapAndSmallFingerJitterToggle() {
        assertTrue(gesture().finish(153f, 304f, 1_100))
    }

    @Test fun tapsAtEveryEdgeAndCornerToggle() {
        for (x in listOf(0f, 20f, 150f, 280f, 299f)) {
            for (y in listOf(0f, 20f, 300f, 580f, 599f)) {
                assertTrue("Tap at ($x, $y)", gesture(x, y).finish(x, y, 1_100))
            }
        }
    }

    @Test fun longPressDoesNotToggle() {
        assertFalse(gesture().finish(150f, 300f, 1_500))
    }

    @Test fun scrollingAwayAndBackIsStillNotATap() {
        val drag = gesture()
        drag.move(150f, 330f, 1)
        assertFalse(drag.finish(150f, 300f, 1_200))
    }

    @Test fun secondFingerCancelsEvenWhenReleasedBeforeFirstFinger() {
        val multiTouch = gesture()
        multiTouch.move(150f, 300f, 2)
        assertFalse(multiTouch.finish(150f, 300f, 1_200))
    }

    @Test fun crossingFormerCenterBoundaryNowToggles() {
        assertTrue(gesture(102f).finish(98f, 300f, 1_100))
    }

    @Test fun contentBoundsFollowLandscapeDimensions() {
        val landscape = ReaderTapGesture(590f, 10f, 0, 600, 300, 12f, 500)
        assertTrue(landscape.finish(590f, 10f, 100))
    }

    @Test fun releasingOutsideContentDoesNotToggle() {
        assertFalse(gesture(2f).finish(-2f, 300f, 1_100))
        assertFalse(gesture(298f).finish(302f, 300f, 1_100))
        assertFalse(gesture(y = 598f).finish(150f, 602f, 1_100))
    }

    @Test fun gestureClaimedByScrollbarDoesNotToggleOnUnconsumedRelease() {
        val scrollbarTap = gesture(290f)
        scrollbarTap.cancel()
        assertFalse(scrollbarTap.finish(290f, 300f, 1_100))
    }
}
