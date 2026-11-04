package io.newsbrief.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import io.newsbrief.app.ui.NewsImageSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imageLoadingAndFailureKeepFollowingContentInPlace() {
        val loaded = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column {
                    NewsImageSlot(145) {
                        if (loaded.value) Text("Loaded", Modifier.fillMaxWidth().height(145.dp))
                    }
                    Text("Article title", Modifier.testTag("title"))
                }
            }
        }
        val before = compose.onNodeWithTag("title").getUnclippedBoundsInRoot().top
        compose.runOnIdle { loaded.value = true }
        val after = compose.onNodeWithTag("title").getUnclippedBoundsInRoot().top
        compose.runOnIdle { loaded.value = false }
        val failed = compose.onNodeWithTag("title").getUnclippedBoundsInRoot().top
        assertEquals(before, after)
        assertEquals(before, failed)
    }
}
