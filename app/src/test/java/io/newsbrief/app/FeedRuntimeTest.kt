package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.features.FeedFeature
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
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
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRuntimeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun minimalFeedLoadsAndOpensExternalBrowserWithoutOptionalUi() {
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        val store = MemoryNewsStore(); val settings = MemorySettings(); val features = listOf(FeedFeature())
        val bindings = FeatureBindings.create(features)
        assertNull(bindings.persistentStorage); assertNull(bindings.article); assertNull(bindings.generate); assertNull(bindings.refresh)
        val sourceId = runBlocking { store.sourceDao().insert(SourceEntity(name = "Fixture RSS", address = "https://example.org/rss", kind = SourceKind.RSS)) }
        var calls = 0
        val repo = NewsRepository(store, object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                calls++
                return listOf(ArticleEntity(sourceId = sourceId, sourceName = source.name, title = "Real feed item", excerpt = "Preview",
                    content = "Full content supplied by the feed", link = "https://example.org/story", publishedAt = 1000))
            }
        }, settings, extensions = bindings)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = NewsViewModel(app, repo, settings, features)
        fun waitFor(test: () -> Boolean) { compose.waitUntil(5000) { dispatcher.scheduler.runCurrent(); compose.waitForIdle(); test() } }
        try {
            compose.setContent { MaterialTheme { NewsBriefScreen(vm) } }
            waitFor { vm.feedArticles.value.size == 1 }
            assertTrue(calls > 0)
            compose.onNodeWithText("Лента").assertExists()
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
            assertNull(vm.readerSource.value)
            compose.onNodeWithText("Full content supplied by the feed").assertDoesNotExist()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
