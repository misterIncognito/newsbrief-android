package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.features.ReadStatusFeature
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.ReadStatusAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadStatusActionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actionReflectsChangesMadeAfterTheArticleWasOpened() {
        val store = MemoryNewsStore()
        val article = runBlocking {
            val sourceId = store.sourceDao().insert(SourceEntity(name = "S", address = "https://example.org/feed", kind = "RSS"))
            val original = ArticleEntity(sourceId = sourceId, sourceName = "S", title = "Post", excerpt = "", link = "https://example.org/a", publishedAt = 1)
            val id = store.articleDao().insertAll(listOf(original)).single()
            original.copy(id = id)
        }
        val bindings = FeatureBindings.create(listOf(ReadStatusFeature()))
        val navigator = object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }
        val host = FeatureHost(ApplicationProvider.getApplicationContext<Application>(), FeatureServices(store, MemorySettings(), bindings = bindings), navigator) { }
        try {
            compose.setContent { MaterialTheme { ReadStatusAction().Content(article, host) } }
            compose.onNodeWithContentDescription("Mark read").assertExists()
            runBlocking { store.articleDao().markVisibleRead(listOf(article.id), 123) }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Mark unread").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Mark unread").performClick()
            compose.waitUntil(10000) { runBlocking { store.articleDao().findByLink(article.link)?.readAt == null } }
        } finally { host.close() }
    }
}
