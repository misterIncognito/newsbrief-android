package io.newsbrief.app

import androidx.compose.ui.geometry.Rect
import io.newsbrief.app.ui.isHeaderVisible
import org.junit.Assert.*
import org.junit.Test

class FeedVisibilityTest {
    private val viewport = Rect(0f, 0f, 400f, 400f)
    @Test fun requiresHalfOfTheActualHeader() {
        assertTrue(isHeaderVisible(Rect(0f, 10f, 300f, 30f), viewport))
        assertTrue(isHeaderVisible(Rect(0f, -10f, 300f, 10f), viewport))
        assertFalse(isHeaderVisible(Rect(0f, -11f, 300f, 9f), viewport))
        assertFalse(isHeaderVisible(Rect(0f, -100f, 300f, -80f), viewport))
        assertFalse(isHeaderVisible(Rect.Zero, viewport))
    }
    @Test fun partiallyVisibleBodyDoesNotMakeAnOffscreenHeaderSeen() {
        assertFalse(isHeaderVisible(Rect(10f, -500f, 300f, -480f), viewport))
        assertFalse(isHeaderVisible(Rect(10f, 401f, 300f, 421f), viewport))
        assertTrue(isHeaderVisible(Rect(10f, 380f, 300f, 400f), viewport))
    }
}
