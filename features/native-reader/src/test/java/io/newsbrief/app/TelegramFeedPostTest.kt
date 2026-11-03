package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.NewsRouting
import io.newsbrief.app.ui.TelegramFeedPost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelegramFeedPostTest {
    @get:Rule val compose = createComposeRule()
    private val url = "https://devby.io/news/story"
    private val body = "Telegram post\n" + (1..8).joinToString("\n") { "Paragraph $it: full saved text." } + "\nПодробнее"
    private val post get() = ArticleEntity(sourceId = 1, sourceName = "dev.by", title = "Post", excerpt = "Truncated preview",
        content = body, link = "https://t.me/devby/16806", publishedAt = 123,
        linksJson = "[{\"label\":\"Подробнее\",\"url\":\"$url\"}]")
    private fun show(text: String = body, onLink: (String) -> Unit = {}, onBrowser: (String) -> Unit = {}) {
        compose.setContent {
            MaterialTheme { TelegramFeedPost(post.copy(content = text), NewsRouting.DEFAULT_WORKER, onLink, onBrowser) }
        }
    }
    @Test fun fullPostIsReadableInFeedWithoutOpeningTelegramDetails() {
        show()
        compose.onNodeWithTag("telegram-post-body").assertTextEquals(body)
        compose.onNodeWithText("Читать в NewsBrief →").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("telegram-post-body").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertTrue(layouts.single().lineCount > 4)
        assertFalse(layouts.single().hasVisualOverflow)
    }
    @Test fun embeddedLinkOpensPublisherDirectlyFromFeed() {
        var opened: String? = null
        val shortPost = "Telegram post\nПодробнее"
        show(text = shortPost, onLink = { opened = it })
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("telegram-post-body").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val position = layouts.single().getBoundingBox(shortPost.indexOf("Подробнее") + 2).center
        compose.onNodeWithTag("telegram-post-body").performTouchInput { click(position) }
        compose.runOnIdle { assertEquals(url, opened) }
    }
    @Test fun browserActionUsesArticleLinkRatherThanTelegramPostUrl() {
        var opened: String? = null
        show(onBrowser = { opened = it })
        compose.onNodeWithText("Открыть в браузере").performClick()
        compose.runOnIdle { assertEquals(url, opened) }
    }
}
