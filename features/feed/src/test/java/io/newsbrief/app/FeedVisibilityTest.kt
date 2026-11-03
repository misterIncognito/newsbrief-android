package io.newsbrief.app

import io.newsbrief.app.ui.isVisibleEnough
import org.junit.Assert.*
import org.junit.Test

class FeedVisibilityTest {
    @Test fun marksOnlySubstantiallyVisibleItems() {
        assertTrue(isVisibleEnough(0, 200, 0, 400))
        assertTrue(isVisibleEnough(-100, 200, 0, 400))
        assertFalse(isVisibleEnough(-101, 200, 0, 400))
        assertFalse(isVisibleEnough(400, 200, 0, 400))
        assertFalse(isVisibleEnough(0, 0, 0, 400))
    }
    @Test fun longPostsCanBeReadWithoutFittingInTheViewport() {
        assertTrue(isVisibleEnough(-500, 1200, 0, 400))
        assertTrue(isVisibleEnough(200, 1200, 0, 400))
        assertFalse(isVisibleEnough(201, 1200, 0, 400))
    }
}
