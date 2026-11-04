package io.newsbrief.app

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.ui.FeedPage
import io.newsbrief.app.ui.FeedUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedSeenIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun onlyVisibleItemsAreMarkedAndScrollingMarksNewItems() {
        val articles = (1L..20L).map { id -> ArticleEntity(id = id, sourceId = 1, sourceName = "S", title = "Post $id",
            excerpt = "", link = "https://example.org/$id", publishedAt = id) }
        val fake = object : FeedUi {
            override val feedLimit = MutableStateFlow(250)
            override fun loadMoreFeed() { feedLimit.value += 250 }
            override val searchQuery = MutableStateFlow("")
            override fun searchNews(query: String) { searchQuery.value = query }
            override val unreadCount = MutableStateFlow(articles.size)
            override val feedArticles = MutableStateFlow(articles)
            override val sources = MutableStateFlow<List<SourceEntity>>(emptyList())
            override val refreshing = MutableStateFlow(false)
            override val savedOnly = MutableStateFlow(false)
            override val filtered = MutableStateFlow(false)
            override fun refresh(quiet: Boolean) = Unit
            override fun feedBecameVisible() = Unit
        }
        val seen = Collections.synchronizedSet(mutableSetOf<Long>())
        val events = Collections.synchronizedList(mutableListOf<Long>())
        compose.setContent {
            MaterialTheme {
                FeedPage(fake, rememberLazyListState(), rememberLazyListState(), onVisible = { seen.addAll(it); events.addAll(it) }) { article ->
                    Text(article.title, Modifier.height(260.dp))
                }
            }
        }
        compose.waitUntil(10000) { seen.contains(1L) }
        assertFalse(seen.contains(20L))
        compose.onNodeWithTag("news-feed").performScrollToIndex(19)
        compose.waitUntil(10000) { seen.contains(20L) }
        compose.onNodeWithTag("news-feed").performScrollToIndex(0)
        compose.waitUntil(10000) { synchronized(events) { events.count { it == 1L } >= 2 } }
    }
}
