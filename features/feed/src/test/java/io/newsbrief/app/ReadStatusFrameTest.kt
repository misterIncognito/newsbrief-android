package io.newsbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadStatusFrameTest {
    @get:Rule val compose = createComposeRule()
    @Test fun swipingEitherDirectionTogglesReadState() {
        var article by mutableStateOf(ArticleEntity(id = 1, sourceId = 1, sourceName = "S", title = "Post", excerpt = "", link = "https://example.org/1", publishedAt = 1))
        var toggles = 0
        compose.setContent {
            MaterialTheme {
                ReadStatusFrame(article, onToggle = { current ->
                    toggles++
                    article = current.copy(readAt = if (current.readAt == null) 123L else null)
                }) { Box(Modifier.fillMaxWidth().height(180.dp)) }
            }
        }
        val frame = compose.onNodeWithTag("read-status-frame-1")
        frame.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unread"))
        frame.performTouchInput { swipeLeft() }
        frame.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Read"))
        frame.performTouchInput { swipeRight() }
        frame.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unread"))
        assertEquals(2, toggles)
    }
}
