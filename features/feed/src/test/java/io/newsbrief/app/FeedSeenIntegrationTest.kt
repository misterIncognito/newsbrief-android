package io.newsbrief.app

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import io.newsbrief.app.ui.LocalFeedHeaderModifier
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
                    Column { Text(article.title, LocalFeedHeaderModifier.current); Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        compose.waitUntil(10000) { seen.contains(1L) }
        assertFalse(seen.contains(20L))
        compose.onNodeWithTag("news-feed").performScrollToIndex(19)
        compose.waitForIdle()
        compose.waitUntil(10000) { seen.contains(20L) }
        compose.onNodeWithTag("news-feed").performScrollToIndex(0)
        compose.waitForIdle()
        compose.waitUntil(10000) { synchronized(events) { events.count { it == 1L } >= 2 } }
    }
    @Test fun changingFeedsScrollsToTop() {
        val articles = (1L..30L).map { id -> ArticleEntity(id = id, sourceId = 1, sourceName = "S", title = "Post $id", excerpt = "", link = "https://example.org/$id", publishedAt = id) }
        val fake = object : FeedUi {
            override val feedLimit = MutableStateFlow(250)
            override fun loadMoreFeed() = Unit
            override val searchQuery = MutableStateFlow("")
            override fun searchNews(query: String) = Unit
            override val unreadCount = MutableStateFlow(0)
            override val feedArticles = MutableStateFlow(articles)
            override val sources = MutableStateFlow<List<SourceEntity>>(emptyList())
            override val refreshing = MutableStateFlow(false)
            override val savedOnly = MutableStateFlow(false)
            override val filtered = MutableStateFlow(false)
            override fun refresh(quiet: Boolean) = Unit
            override fun feedBecameVisible() = Unit
        }
        val identity = mutableStateOf("all")
        lateinit var state: LazyListState
        compose.setContent {
            MaterialTheme {
                state = rememberLazyListState()
                FeedPage(fake, state, rememberLazyListState(), feedIdentity = identity.value) { article ->
                    Text(article.title, LocalFeedHeaderModifier.current.height(260.dp))
                }
            }
        }
        compose.onNodeWithTag("news-feed").performScrollToIndex(15)
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0); identity.value = "another feed" }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemIndex); assertEquals(0, state.firstVisibleItemScrollOffset) }
    }

    @Test fun bodyVisibleWithoutHeaderDoesNotMarkPostRead() {
        val article = ArticleEntity(id = 1, sourceId = 1, sourceName = "S", title = "Long post", excerpt = "", link = "https://example.org/long", publishedAt = 1)
        val fake = object : FeedUi {
            override val feedLimit = MutableStateFlow(250)
            override fun loadMoreFeed() = Unit
            override val searchQuery = MutableStateFlow("")
            override fun searchNews(query: String) = Unit
            override val unreadCount = MutableStateFlow(1)
            override val feedArticles = MutableStateFlow(listOf(article))
            override val sources = MutableStateFlow<List<SourceEntity>>(emptyList())
            override val refreshing = MutableStateFlow(false)
            override val savedOnly = MutableStateFlow(false)
            override val filtered = MutableStateFlow(false)
            override fun refresh(quiet: Boolean) = Unit
            override fun feedBecameVisible() = Unit
        }
        val seen = Collections.synchronizedSet(mutableSetOf<Long>())
        lateinit var state: LazyListState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        compose.setContent { MaterialTheme {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            FeedPage(fake, state, rememberLazyListState(), onVisible = { seen.addAll(it) }) {
                Column { Text("Header", LocalFeedHeaderModifier.current); Spacer(Modifier.height(1400.dp)) }
            }
        } }
        compose.runOnIdle { scope.launch { state.scrollToItem(0, 400) } }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(state.firstVisibleItemScrollOffset > 0) }
        Thread.sleep(2500)
        compose.waitForIdle()
        assertFalse(seen.contains(1L))
        compose.runOnIdle { scope.launch { state.scrollToItem(0) } }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemScrollOffset) }
        compose.waitUntil(10000) { seen.contains(1L) }
    }

}
