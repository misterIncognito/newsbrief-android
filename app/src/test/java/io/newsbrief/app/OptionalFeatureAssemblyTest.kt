package io.newsbrief.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.runtime.*
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
class OptionalFeatureAssemblyTest {
    @get:Rule val compose = createComposeRule()
    private val selected get() = System.getProperty("newsbrief.expectedFeatures", "").split(',').filter { it.isNotEmpty() }.toSet()
    @Test fun onlySelectedFeaturesRegisterTheirOwnControllers() {
        val bindings = FeatureBindings.create(InstalledFeatures.create())
        val owners = mapOf("feed" to FeedUi::class.java, "feed-filters" to FeedFilterUi::class.java,
            "save-articles" to SavedUi::class.java, "ai" to AiUi::class.java,
            "source-management" to SourcesUi::class.java, "source-discovery" to DirectoryUi::class.java,
            "telegram-proxy" to TelegramProxyUi::class.java, "news-proxy" to WorkerSettingsUi::class.java,
            "native-reader" to ReaderUi::class.java, "embedded-browser" to BrowserUi::class.java)
        assertEquals(owners.filterKeys { it in selected }.values.toSet(), bindings.controllers.keys)
    }
    @Test fun feedAcceptsOptionalControlsAndKeepsDefaultExternalNavigation() {
        if ("feed" !in selected) return
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = MemoryNewsStore(); val settings = MemorySettings().apply {
            markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded(); markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
        }
        val features = InstalledFeatures.create(); val bindings = FeatureBindings.create(features)
        val id = runBlocking { store.sourceDao().insert(SourceEntity(name = "Fixture", address = "https://fixture.example/rss", kind = SourceKind.RSS)) }
        val article = ArticleEntity(sourceId = id, sourceName = "Fixture", title = "Independent feature story", excerpt = "Preview",
            content = "Full offline reader content", readerLoaded = true, link = "https://fixture.example/story", publishedAt = 1)
        runBlocking { store.articleDao().insertAll(listOf(article)) }
        val collector = object : NewsCollector { override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>() }
        val services = FeatureServices(store, settings, collector, bindings = bindings)
        val shell = NewsViewModel(app, services, features)
        fun waitFor(test: () -> Boolean) = compose.waitUntil(5000) { dispatcher.scheduler.runCurrent(); compose.waitForIdle(); test() }
        try {
            compose.setContent { MaterialTheme { NewsBriefScreen(shell) } }
            waitFor { shell.host.controller(FeedUi::class.java).feedArticles.value.size == 1 }
            compose.onNodeWithText("Independent feature story").assertExists()
            if ("save-articles" in selected) {
                compose.onNodeWithText("Сохранить").performScrollTo().performClick()
                waitFor { article.link in shell.host.controller(SavedUi::class.java).savedUrls.value }
                compose.onNodeWithText("✓ Сохранено").assertExists()
            } else {
                compose.onNodeWithText("Сохранить").assertDoesNotExist()
                compose.onNodeWithText("Сохранённые").assertDoesNotExist()
                assertNull(shell.host.optional(SavedUi::class.java))
            }
            if ("share-articles" in selected) compose.onNodeWithText("Поделиться").assertExists()
            else compose.onNodeWithText("Поделиться").assertDoesNotExist()
            if ("feed-filters" in selected) {
                compose.onNodeWithTag("open-source-filter").performClick()
                compose.onNodeWithTag("source-filter-panel").assertExists()
                compose.onNodeWithText("Готово").performClick()
            } else compose.onNodeWithTag("open-source-filter").assertDoesNotExist()
            if ("native-reader" !in selected && "embedded-browser" !in selected) {
                compose.onNodeWithText("Independent feature story").performScrollTo().performClick()
                val intent = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
                assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
                assertEquals(article.link, intent.data.toString())
                assertTrue(shell.overlays.value.isEmpty())
            }
        } finally { shell.closeFeatures(); Dispatchers.resetMain() }
    }
    @Test fun availableNavigationPluginsRegisterTheirOwnOverlayAndKeepReaderHistory() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val features = InstalledFeatures.create(); val bindings = FeatureBindings.create(features)
        val services = FeatureServices(MemoryNewsStore(), MemorySettings(), bindings = bindings)
        val shell = NewsViewModel(app, services, features)
        val article = ArticleEntity(sourceId = 0, sourceName = "Fixture", title = "Offline", excerpt = "", content = "Cached", readerLoaded = true,
            link = "https://fixture.example/story", publishedAt = 1)
        try {
            shell.host.openArticle(article)
            when {
                "native-reader" in selected -> {
                    assertEquals("reader", shell.overlays.value.last().id)
                    assertEquals(article.link, shell.host.controller(ReaderUi::class.java).readerSource.value!!.link)
                    if ("embedded-browser" in selected) {
                        shell.host.openBrowser(article.link, true)
                        assertEquals(listOf("reader", "browser"), shell.overlays.value.map { it.id })
                        shell.overlays.value.last().onBack()
                        assertEquals("reader", shell.overlays.value.last().id)
                    }
                    shell.overlays.value.last().onBack()
                    assertTrue(shell.overlays.value.isEmpty())
                }
                "embedded-browser" in selected -> {
                    assertEquals("browser", shell.overlays.value.last().id)
                    shell.overlays.value.last().onBack()
                    assertTrue(shell.overlays.value.isEmpty())
                }
                else -> assertTrue(shell.overlays.value.isEmpty())
            }
        } finally { shell.closeFeatures(); Dispatchers.resetMain() }
    }
}
