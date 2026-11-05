package io.newsbrief.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UnreadSessionTest {
    @Test fun refreshAndSwitchingFeedClearSessionButQuietRefreshDoesNot() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = MemoryNewsStore()
        val source = store.sourceDao().insert(SourceEntity(name = "Feed", address = "https://example.org", kind = SourceKind.RSS))
        val id = store.articleDao().insertAll(listOf(ArticleEntity(sourceId = source, sourceName = "Feed", title = "One", excerpt = "", link = "https://example.org/1", publishedAt = 1))).single()
        val read = object : ViewModel(), ReadStatusUi {
            override val unreadOnly = MutableStateFlow(true)
            override val visibleReadIds = MutableStateFlow<Set<Long>>(emptySet())
            override fun clearUnreadSession() { visibleReadIds.value = emptySet() }
            override fun setUnreadOnly(value: Boolean) { unreadOnly.value = value }
            override fun toggleRead(article: ArticleEntity): Job = Job().apply { complete() }
            override fun retainVisible(ids: List<Long>) = Unit
            override suspend fun markVisible(ids: List<Long>) { visibleReadIds.value += ids; store.articleDao().markVisibleRead(ids, 5) }
        }
        val filter = object : ViewModel(), FeedFilterUi {
            override val feedCategory = MutableStateFlow(FeedCategory.ALL)
            override val availableCategories = MutableStateFlow(listOf(FeedCategory.ALL))
            override val feedSources = MutableStateFlow<Set<String>>(emptySet())
            override val feedOptions = MutableStateFlow<List<FeedSourceOption>>(emptyList())
            override val selection = MutableStateFlow(FeedSelection())
            override fun filterCategory(category: FeedCategory) { selection.value = FeedSelection(category) }
            override fun filterSource(key: String?) { selection.value = FeedSelection(keys = key?.let { setOf(it) }.orEmpty()) }
            override fun toggleFeedSource(key: String) = filterSource(key)
            override fun clearFeedFilter() { selection.value = FeedSelection() }
        }
        val bindings = FeatureBindings.create(emptyList())
        bindings.controller(ReadStatusUi::class.java) { read }
        bindings.controller(FeedFilterUi::class.java) { filter }
        val host = FeatureHost(ApplicationProvider.getApplicationContext<Application>(), FeatureServices(store, MemorySettings(), bindings = bindings), object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }) { }
        val model = FeedViewModel(host)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.feedArticles.collect() }
        try {
            runCurrent()
            read.markVisible(listOf(id)); runCurrent()
            assertEquals(listOf(id), model.feedArticles.value.map { it.id })
            model.refresh(quiet = true); runCurrent()
            assertEquals(listOf(id), model.feedArticles.value.map { it.id })
            model.refresh(); runCurrent()
            assertTrue(model.feedArticles.value.isEmpty())
            store.articleDao().setReadAt("https://example.org/1", null); runCurrent()
            read.markVisible(listOf(id)); runCurrent()
            assertEquals(listOf(id), model.feedArticles.value.map { it.id })
            filter.filterSource("other"); runCurrent()
            assertTrue(read.visibleReadIds.value.isEmpty())
            filter.clearFeedFilter(); runCurrent()
            assertTrue(model.feedArticles.value.isEmpty())
        } finally { observer.cancel(); host.close(); Dispatchers.resetMain() }
    }
}
