package io.newsbrief.app

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.NewsBriefScreen
import io.newsbrief.app.ui.NewsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunWizardIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clearSetup() { app.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun userSelectsCustomSourceAndFinishesIntoUpdatedFeed() {
        val features = InstalledFeatures.create()
        if (!setOf("source-setup", "source-management", "source-discovery", "source-providers", "rss-source", "feed").all { wanted -> features.any { it.id == wanted } }) return
        val dispatcher = UnconfinedTestDispatcher(); Dispatchers.setMain(dispatcher)
        val store = MemoryNewsStore(); val calls = AtomicInteger()
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                calls.incrementAndGet()
                return listOf(ArticleEntity(sourceId = source.id, sourceName = source.name, title = "Chosen news", excerpt = "", link = "https://example.org/story", publishedAt = System.currentTimeMillis()))
            }
        }
        val bindings = FeatureBindings.create(features)
        val shell = NewsViewModel(app, FeatureServices(store, MemorySettings(), collector, bindings = bindings), features)
        fun waitFor(test: () -> Boolean) = compose.waitUntil(15000) { dispatcher.scheduler.runCurrent(); test() }
        try {
            compose.setContent { MaterialTheme { NewsBriefScreen(shell) } }
            waitFor { compose.onAllNodesWithText("Welcome to NewsBrief").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, runBlocking { store.sourceDao().count() })
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Choose sources from the catalog").assertExists()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Add a source").assertIsDisplayed()
            compose.onNodeWithText("Name").performTextInput("My source")
            compose.onNodeWithText("HTTPS feed URL for RSS or Atom").performTextInput("https://example.org/feed")
            compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
            waitFor { runBlocking { store.sourceDao().count() == 1 } }
            waitFor { compose.onAllNodesWithText("Finish and update feed").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Add a source").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Finish and update feed").performClick()
            waitFor { calls.get() > 0 && compose.onAllNodesWithText("Chosen news").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE).getBoolean("completed", false))
        } finally { shell.closeFeatures(); Dispatchers.resetMain() }
    }
}
