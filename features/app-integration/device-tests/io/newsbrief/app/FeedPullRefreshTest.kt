package io.newsbrief.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.newsbrief.app.ui.FeedRefreshContainer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedPullRefreshTest {
    @get:Rule val compose = createComposeRule()
    private var refreshes = 0
    private fun showFeed(empty: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                FeedRefreshContainer(false, { refreshes++ }, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("feed")) {
                        if (empty) item { Text("Empty feed", Modifier.fillParentMaxHeight()) }
                        else items(30) { Text("Article $it", Modifier.height(100.dp)) }
                    }
                }
            }
        }
    }
    private fun pull(twoFingers: Boolean = false) {
        compose.onNodeWithTag("feed").performTouchInput {
            val start = Offset(centerX, height * .2f)
            down(0, start)
            if (twoFingers) down(1, start + Offset(80f, 0f))
            for (step in 1..16) {
                val position = start + Offset(0f, height * .55f * step / 16)
                moveTo(0, position, delayMillis = 16)
                if (twoFingers) moveTo(1, position + Offset(80f, 0f), delayMillis = 16)
            }
            if (twoFingers) up(1)
            up(0)
        }
        compose.waitForIdle()
    }
    @Test fun oneFingerPullAtTopRefreshesOnce() {
        showFeed(); pull(); assertEquals(1, refreshes)
    }
    @Test fun twoFingerPullAtTopRefreshesOnce() {
        showFeed(); pull(true); assertEquals(1, refreshes)
    }
    @Test fun emptyFeedCanAlsoBePulledToRefresh() {
        showFeed(true); pull(); assertEquals(1, refreshes)
    }
    @Test fun pullingWithinScrolledListDoesNotRefresh() {
        showFeed()
        compose.onNodeWithTag("feed").performScrollToIndex(10)
        pull(); assertEquals(0, refreshes)
    }
}
