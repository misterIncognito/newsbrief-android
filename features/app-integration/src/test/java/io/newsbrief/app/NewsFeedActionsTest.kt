package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.ui.NewsBriefScreen
import io.newsbrief.app.ui.NewsViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class NewsFeedActionsTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    @Test fun readerReturnWithinCooldownPreservesScrollAndShowsSavedItemInSavedFeed() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        // Room callbacks and screen effects need both the coroutine scheduler and a UI frame.
        fun waitFor(condition: () -> Boolean) {
            compose.waitUntil(5000) {
                dispatcher.scheduler.runCurrent()
                compose.waitForIdle()
                condition()
            }
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var model: NewsViewModel? = null
        try {
            val settings = SettingsStore(app).apply {
                markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
                markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
            }
            runBlocking { db.sourceDao().insert(SourceEntity(name = "Source", address = "https://source.example/rss", kind = SourceKind.RSS)) }
            val calls = AtomicInteger()
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                    calls.incrementAndGet()
                    return (0..29).map { index -> ArticleEntity(sourceId = source.id, sourceName = source.name,
                        title = "Story $index", excerpt = "Preview $index", content = "Saved reader body $index", readerLoaded = true,
                        link = "https://source.example/story/$index", publishedAt = 1000L - index) }
                }
            }, settings)
            lateinit var vm: NewsViewModel
            compose.runOnIdle { vm = NewsViewModel(app, repo, settings); model = vm }
            val owner = Owner()
            compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme { NewsBriefScreen(vm) }
            } }
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
            waitFor { calls.get() >= 1 && !vm.refreshing.value }
            compose.onNodeWithText("Лента").performClick()
            waitFor { calls.get() >= 1 && !vm.refreshing.value && vm.feedArticles.value.size == 30 }
            compose.onNodeWithTag("news-feed").performScrollToIndex(15)
            compose.onNodeWithText("Story 15", substring = true).performClick()
            waitFor { vm.reader.value?.text == "Saved reader body 15" }
            compose.onNodeWithText("Сохранить").performClick()
            waitFor { "https://source.example/story/15" in vm.savedUrls.value }
            compose.onNodeWithText("✓ Сохранено").assertExists()
            val beforeReturn = calls.get()
            compose.onNodeWithText("← Назад").performClick()
            waitFor { vm.readerSource.value == null && calls.get() == beforeReturn && !vm.refreshing.value }
            compose.onNodeWithText("Story 15", substring = true).assertIsDisplayed()
            assertEquals(beforeReturn, calls.get())
            compose.onNodeWithText("Сохранённые").performClick()
            waitFor { vm.feedArticles.value.size == 1 }
            compose.onNodeWithText("Story 15", substring = true).assertExists()
            compose.onNodeWithText("✓ Сохранено").performClick()
            waitFor { vm.feedArticles.value.isEmpty() }
            compose.onNodeWithText("Здесь будут сохранённые публикации.", substring = true).assertExists()
            assertNotNull(runBlocking { repo.lookup("https://source.example/story/15") })
        } finally { model?.viewModelScope?.cancel(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun sourceScreenListsRegisteredPluginsAndExplainsUnconfiguredTelegramProxy() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        fun waitFor(condition: () -> Boolean) { compose.waitUntil(5000) {
            dispatcher.scheduler.runCurrent(); compose.waitForIdle(); condition()
        } }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var model: NewsViewModel? = null
        try {
            val settings = SettingsStore(app)
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, settings)
            lateinit var vm: NewsViewModel
            compose.runOnIdle { vm = NewsViewModel(app, repo, settings); model = vm }
            val owner = Owner()
            compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme { NewsBriefScreen(vm) }
            } }
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.onNodeWithText("Источники").performClick()
            compose.onNodeWithText("Своя ссылка").performClick()
            vm.sourcePlugins.forEach { compose.onNode(hasText(it.title) and hasClickAction()).assertExists() }
            compose.onNodeWithText("Telegram через прокси").performClick()
            compose.onNodeWithText("@канал, ссылка t.me или ID канала").assertExists()
            compose.onNodeWithText("Отмена").performClick()
            compose.onNodeWithText("Telegram-прокси").performClick()
            waitFor { vm.proxyChannels.value.error != null }
            compose.onNodeWithText("Настрой HTTPS-адрес и ключ Telegram-прокси", substring = true).assertExists()
            compose.onNodeWithText("Закрыть").performClick()
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("HTTPS URL Telegram-прокси").performScrollTo().assertExists()
            compose.onNodeWithText("Ключ доступа Telegram-прокси").performScrollTo().assertExists()
        } finally { model?.viewModelScope?.cancel(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun multipleSourceChipsCombineFeedAndBrowserActionStaysAboveArticle() {
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        fun waitFor(condition: () -> Boolean) { compose.waitUntil(5000) {
            dispatcher.scheduler.runCurrent(); compose.waitForIdle(); condition()
        } }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var model: NewsViewModel? = null
        try {
            val settings = SettingsStore(app).apply {
                markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded(); markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
            }
            runBlocking { listOf("Alpha", "Beta", "Gamma").forEach { name ->
                db.sourceDao().insert(SourceEntity(name = name, address = "https://${name.lowercase()}.example/rss", kind = SourceKind.RSS))
            } }
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = listOf(ArticleEntity(sourceId = source.id,
                    sourceName = source.name, title = "Story ${source.name}", excerpt = "Preview",
                    content = "Long article text. ".repeat(1000), readerLoaded = true,
                    link = source.address.replace("rss", "story"), publishedAt = 123))
            }, settings)
            lateinit var vm: NewsViewModel
            compose.runOnIdle { vm = NewsViewModel(app, repo, settings); model = vm }
            val owner = Owner()
            compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { MaterialTheme { NewsBriefScreen(vm) } } }
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.onNodeWithText("Лента").performClick()
            waitFor { vm.feedArticles.value.size == 3 }
            compose.onNodeWithText("Alpha").performScrollTo().performClick()
            waitFor { vm.feedArticles.value.size == 1 }
            compose.onNodeWithText("Beta").performScrollTo().performClick()
            waitFor { vm.feedArticles.value.size == 2 }
            compose.onNodeWithText("Alpha").assertIsSelected(); compose.onNodeWithText("Beta").assertIsSelected()
            compose.onNodeWithText("Alpha").performScrollTo().performClick()
            waitFor { vm.feedArticles.value.singleOrNull()?.sourceName == "Beta" }
            compose.onNodeWithText("Все источники").performScrollTo().performClick()
            waitFor { vm.feedArticles.value.size == 3 }
            compose.onNodeWithText("Story Alpha").performClick()
            waitFor { vm.reader.value != null }
            val browser = compose.onNodeWithText("Открыть во встроенном браузере")
            browser.assertIsDisplayed()
            assertTrue(browser.fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Story Alpha").fetchSemanticsNode().boundsInRoot.top)
        } finally { model?.viewModelScope?.cancel(); db.close(); Dispatchers.resetMain() }
    }

}
