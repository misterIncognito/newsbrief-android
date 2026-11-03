package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.features.FeedFeature
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRuntimeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedSourcesFilterTheFeedAndResetRestoresAllArticles() {
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        val store = MemoryNewsStore(); val features = listOf(FeedFeature())
        val ids = runBlocking {
            val first = store.sourceDao().insert(SourceEntity(name = "First", address = "https://first.example/rss", kind = SourceKind.RSS))
            val second = store.sourceDao().insert(SourceEntity(name = "Second", address = "https://second.example/rss", kind = SourceKind.RSS))
            store.articleDao().insertAll(listOf(
                ArticleEntity(sourceId = first, sourceName = "First", title = "First article", excerpt = "", link = "https://first.example/1", publishedAt = 1),
                ArticleEntity(sourceId = second, sourceName = "Second", title = "Second article", excerpt = "", link = "https://second.example/1", publishedAt = 2)))
            listOf(first, second)
        }
        val filter = object : ViewModel(), FeedFilterUi {
            override val feedCategory = MutableStateFlow(FeedCategory.ALL)
            override val availableCategories = MutableStateFlow(listOf(FeedCategory.ALL))
            override val feedSources = MutableStateFlow<Set<String>>(emptySet())
            override val feedOptions = MutableStateFlow<List<FeedSourceOption>>(emptyList())
            override val selection = MutableStateFlow(FeedSelection())
            override fun filterCategory(category: FeedCategory) { feedCategory.value = category; feedSources.value = emptySet(); selection.value = FeedSelection(category) }
            override fun filterSource(key: String?) { feedSources.value = key?.let { setOf(it) }.orEmpty(); selection.value = FeedSelection(feedCategory.value, feedSources.value) }
            override fun toggleFeedSource(key: String) {
                feedSources.value = if (key in feedSources.value) feedSources.value - key else feedSources.value + key
                selection.value = FeedSelection(feedCategory.value, feedSources.value)
            }
            override fun clearFeedFilter() = filterCategory(FeedCategory.ALL)
        }
        val bindings = FeatureBindings.create(features)
        bindings.controller(FeedFilterUi::class.java) { filter }
        val services = io.newsbrief.app.runtime.FeatureServices(store, MemorySettings(), bindings = bindings)
        val shell = NewsViewModel(ApplicationProvider.getApplicationContext<Application>(), services, features)
        fun waitForTitles(titles: Set<String>) {
            compose.waitUntil(5000) {
                dispatcher.scheduler.runCurrent(); compose.waitForIdle()
                shell.host.controller(FeedUi::class.java).feedArticles.value.map { it.title }.toSet() == titles
            }
        }
        try {
            compose.setContent { MaterialTheme { NewsBriefScreen(shell) } }
            waitForTitles(setOf("First article", "Second article"))
            filter.toggleFeedSource("source:${ids[0]}")
            waitForTitles(setOf("First article"))
            filter.toggleFeedSource("source:${ids[1]}")
            waitForTitles(setOf("First article", "Second article"))
            filter.toggleFeedSource("source:${ids[0]}")
            waitForTitles(setOf("Second article"))
            filter.clearFeedFilter()
            waitForTitles(setOf("First article", "Second article"))
        } finally { shell.closeFeatures(); Dispatchers.resetMain() }
    }
    @Test fun minimalFeedLoadsAndOpensExternalBrowserWithoutOptionalUi() {
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        val store = MemoryNewsStore(); val settings = MemorySettings(); val features = listOf(FeedFeature())
        val bindings = FeatureBindings.create(features)
        assertNull(bindings.persistentStorage); assertNull(bindings.article); assertNull(bindings.generate); assertNull(bindings.refresh)
        val sourceId = runBlocking { store.sourceDao().insert(SourceEntity(name = "Fixture RSS", address = "https://example.org/rss", kind = SourceKind.RSS)) }
        var calls = 0
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                calls++
                return listOf(ArticleEntity(sourceId = sourceId, sourceName = source.name, title = "Real feed item", excerpt = "Preview",
                    content = "Full content supplied by the feed", link = "https://example.org/story", publishedAt = 1000))
            }
        }
        bindings.feedLoader = { FeedLoader {
            val source = runBlocking { store.sourceDao().enabled().single() }
            val items = collector.collect(source)
            val inserted = store.articleDao().insertAll(items)
            RefreshResult(inserted.count { it != -1L }, 1, emptyList())
        } }
        val services = io.newsbrief.app.runtime.FeatureServices(store, settings, collector, bindings = bindings)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = NewsViewModel(app, services, features)
        fun waitFor(test: () -> Boolean) { compose.waitUntil(5000) { dispatcher.scheduler.runCurrent(); compose.waitForIdle(); test() } }
        try {
            compose.setContent { MaterialTheme { NewsBriefScreen(vm) } }
            waitFor { vm.host.controller(FeedUi::class.java).feedArticles.value.size == 1 }
            assertTrue(calls > 0)
            compose.onNodeWithText("Лента").assertExists()
            compose.onNodeWithText("MVP").performClick()
            compose.onNodeWithText("Это ранняя версия приложения. Она может содержать ошибки и работать непредсказуемо.").assertExists()
            compose.onNodeWithText("OK").performClick()
            compose.onNodeWithText("Ранняя версия").assertDoesNotExist()
            compose.onNodeWithText("Главное").assertDoesNotExist()
            compose.onNodeWithText("Источники").assertDoesNotExist()
            compose.onNodeWithText("Каталог").assertDoesNotExist()
            compose.onNodeWithText("Настройки").assertDoesNotExist()
            compose.onNodeWithText("Сохранить").assertDoesNotExist()
            compose.onNodeWithText("Поделиться").assertDoesNotExist()
            compose.onNodeWithText("Сохранённые").assertDoesNotExist()
            compose.onNodeWithText("Технологии").assertDoesNotExist()
            compose.onNodeWithText("Real feed item").performClick()
            val intent = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
            assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
            assertEquals("https://example.org/story", intent.data.toString())
            assertTrue(vm.overlays.value.isEmpty())
            compose.onNodeWithText("Full content supplied by the feed").assertDoesNotExist()
        } finally { vm.closeFeatures(); Dispatchers.resetMain() }
    }
}
