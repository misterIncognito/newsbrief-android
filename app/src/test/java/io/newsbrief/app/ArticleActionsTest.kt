package io.newsbrief.app

import android.app.Application
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.ui.ArticleActions
import io.newsbrief.app.ui.articleShareIntent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArticleActionsTest {
    @get:Rule val compose = createComposeRule()
    private val article = ArticleEntity(sourceId = 1, sourceName = "Source", title = "Readable title",
        excerpt = "Preview", link = "https://meduza.io/news/original", publishedAt = 123, proxyRequired = true)
    @Test fun sharePayloadUsesOriginalArticleUrlAndTitle() {
        val intent = articleShareIntent(article)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(article.title, intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("${article.title}\n${article.link}", intent.getStringExtra(Intent.EXTRA_TEXT))
        val post = article.copy(link = "https://t.me/channel/123")
        assertTrue(articleShareIntent(post).getStringExtra(Intent.EXTRA_TEXT)!!.endsWith(post.link))
    }
    @Test fun saveButtonTogglesVisibleStateAndShareOpensSystemChooser() {
        var saved by mutableStateOf(false)
        compose.setContent { MaterialTheme { ArticleActions(article, saved) { saved = !saved } } }
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("✓ Сохранено").assertExists().performClick()
        compose.onNodeWithText("Сохранить").assertExists()
        compose.onNodeWithText("Поделиться").performClick()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("${article.title}\n${article.link}", intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
