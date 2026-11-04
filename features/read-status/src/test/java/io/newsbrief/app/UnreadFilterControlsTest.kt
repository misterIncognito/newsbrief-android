package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.ui.ReadStatusUi
import io.newsbrief.app.ui.UnreadFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnreadFilterControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggleExplainsBothFilterStates() {
        val status = object : ReadStatusUi {
            override val unreadOnly = MutableStateFlow(false)
            override val visibleReadIds = MutableStateFlow<Set<Long>>(emptySet())
            override fun setUnreadOnly(value: Boolean) { unreadOnly.value = value }
            override fun toggleRead(article: ArticleEntity) = Job()
            override fun retainVisible(ids: List<Long>) = Unit
            override suspend fun markVisible(ids: List<Long>) = Unit
        }
        compose.setContent { MaterialTheme { UnreadFilter(status) } }
        compose.onNodeWithTag("unread-filter").assertIsOff().performClick().assertIsOn()
        assertEquals("Showing unread articles only", ShadowToast.getTextOfLatestToast())
        compose.onNodeWithTag("unread-filter").performClick().assertIsOff()
        assertEquals("Showing all articles", ShadowToast.getTextOfLatestToast())
    }
}
