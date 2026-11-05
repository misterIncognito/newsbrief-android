package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.newsbrief.app.data.*
import io.newsbrief.app.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelFilterBarTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectingChannelAndAllUpdatesSharedFilterAndCounts() {
        val store = MemoryNewsStore()
        runBlocking {
            val a = store.sourceDao().insert(SourceEntity(name = "Alpha", address = "https://a.example/feed", kind = SourceKind.RSS))
            val b = store.sourceDao().insert(SourceEntity(name = "Beta", address = "https://b.example/feed", kind = SourceKind.RSS))
            store.articleDao().insertAll(listOf(
                ArticleEntity(sourceId = a, sourceName = "Alpha", title = "Unread", excerpt = "", link = "https://a.example/1", publishedAt = 1),
                ArticleEntity(sourceId = b, sourceName = "Beta", title = "Read", excerpt = "", link = "https://b.example/1", publishedAt = 1, readAt = 10)))
        }
        val filter = object : FeedFilterUi {
            override val feedCategory = MutableStateFlow(FeedCategory.ALL)
            override val availableCategories = MutableStateFlow(listOf(FeedCategory.ALL))
            override val feedSources = MutableStateFlow<Set<String>>(emptySet())
            override val feedOptions = MutableStateFlow(listOf(FeedSourceOption("a", "Alpha", listOf(1)), FeedSourceOption("b", "Beta", listOf(2))))
            override val selection = MutableStateFlow(FeedSelection())
            override fun filterCategory(category: FeedCategory) { feedCategory.value = category; feedSources.value = emptySet() }
            override fun filterSource(key: String?) { feedSources.value = key?.let(::setOf).orEmpty() }
            override fun toggleFeedSource(key: String) { feedSources.value = if (key in feedSources.value) feedSources.value - key else feedSources.value + key }
            override fun clearFeedFilter() { filterCategory(FeedCategory.ALL) }
        }
        compose.setContent { MaterialTheme { ChannelFilterBar(filter, store.articleDao()) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("All channels · 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alpha · 1").assertExists().performClick()
        compose.runOnIdle { assertEquals(setOf("a"), filter.feedSources.value) }
        compose.onNodeWithTag("channel-all").performTouchInput { longClick() }
        compose.onNodeWithTag("channel-multiselect").assertExists()
        compose.onNodeWithTag("channel-menu-b").performClick()
        compose.runOnIdle { assertEquals(setOf("a", "b"), filter.feedSources.value) }
        compose.onNodeWithTag("channel-menu-a").performClick()
        compose.runOnIdle { assertEquals(setOf("b"), filter.feedSources.value) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("channel-all").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), filter.feedSources.value) }
        runBlocking { store.articleDao().setReadAt("https://a.example/1", 100) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("All channels · 0").fetchSemanticsNodes().isNotEmpty() }
    }
}
