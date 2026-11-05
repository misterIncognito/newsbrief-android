package io.newsbrief.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.MemoryNewsStore
import io.newsbrief.app.data.MemorySettings
import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.network.DirectoryProvider
import io.newsbrief.app.network.DirectorySource
import io.newsbrief.app.network.SourcePluginInfo
import io.newsbrief.app.runtime.*
import io.newsbrief.app.features.FeedFiltersFeature
import io.newsbrief.app.ui.DirectoryState
import io.newsbrief.app.ui.DirectoryUi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.newsbrief.app.data.FeedCategory
import io.newsbrief.app.data.FeedSourceOption
import io.newsbrief.app.ui.FeedFilterControls
import io.newsbrief.app.ui.FeedFilterUi
import io.newsbrief.app.ui.FeedSelection
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedFilterPanelTest {
    @get:Rule val compose = createComposeRule()

    private class FakeFilter : ViewModel(), FeedFilterUi {
        override val feedCategory = MutableStateFlow(FeedCategory.ALL)
        override val availableCategories = MutableStateFlow(listOf(FeedCategory.ALL, FeedCategory.TECH))
        override val feedSources = MutableStateFlow<Set<String>>(emptySet())
        override val feedOptions = MutableStateFlow(listOf(
            FeedSourceOption("verge", "The Verge", listOf(1)),
            FeedSourceOption("zerkalo", "Зеркало", listOf(2))))
        override val selection = MutableStateFlow(FeedSelection())
        private fun update() { selection.value = FeedSelection(feedCategory.value, feedSources.value) }
        override fun filterCategory(category: FeedCategory) { feedCategory.value = category; feedSources.value = emptySet(); update() }
        override fun filterSource(key: String?) { feedSources.value = key?.let { setOf(it) }.orEmpty(); update() }
        override fun toggleFeedSource(key: String) {
            feedSources.value = if (key in feedSources.value) feedSources.value - key else feedSources.value + key
            update()
        }
        override fun clearFeedFilter() { filterCategory(FeedCategory.ALL) }
    }

    @Test fun sidePanelSupportsMultipleSourcesSearchAndReset() {
        val vm = FakeFilter()
        compose.setContent { MaterialTheme { FeedFilterControls(vm) } }
        compose.onNodeWithTag("source-filter-panel").assertDoesNotExist()
        compose.onNodeWithTag("open-source-filter").performClick()
        compose.onNodeWithTag("source-filter-panel").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("source-filter-panel").fetchSemanticsNode().boundsInRoot.left > 0f)
        compose.onNodeWithText("Беларусь").assertDoesNotExist()
        compose.onNodeWithText("Польша").assertDoesNotExist()
        compose.onNodeWithText("The Verge").performClick()
        compose.onNodeWithText("Зеркало").performClick()
        compose.onNodeWithText("The Verge").assertIsOn()
        compose.onNodeWithText("Зеркало").assertIsOn()
        compose.runOnIdle { assertEquals(setOf("verge", "zerkalo"), vm.feedSources.value) }
        compose.onNodeWithTag("source-filter-search").performTextInput("Verge")
        compose.onNodeWithText("The Verge").assertExists()
        compose.onNodeWithText("Зеркало").assertDoesNotExist()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithTag("source-filter-panel").assertDoesNotExist()
        compose.onNodeWithTag("open-source-filter").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), vm.feedSources.value) }
        compose.onNodeWithText("Показаны все источники").assertExists()
    }
    @Test fun missingSourceCanBeSearchedOnlineWithTheEnteredQuery() {
        val vm = FakeFilter()
        var requested: String? = null
        compose.setContent { MaterialTheme { FeedFilterControls(vm, searchOnline = { requested = it }) } }
        compose.onNodeWithTag("open-source-filter").performClick()
        compose.onNodeWithTag("search-online-sources").assertDoesNotExist()
        compose.onNodeWithTag("source-filter-search").performTextInput("  Kotlin Daily  ")
        compose.onNodeWithTag("search-online-sources").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("Kotlin Daily", requested) }
        compose.onNodeWithTag("source-filter-panel").assertDoesNotExist()
    }

    @Test fun onlineButtonIsAbsentWhenDiscoveryIsUnavailable() {
        val vm = FakeFilter()
        compose.setContent { MaterialTheme { FeedFilterControls(vm) } }
        compose.onNodeWithTag("open-source-filter").performClick()
        compose.onNodeWithTag("source-filter-search").performTextInput("missing")
        compose.onNodeWithTag("search-online-sources").assertDoesNotExist()
    }

    @Test fun onlineButtonOpensCatalogWithRequestedSearch() {
        val filter = FakeFilter()
        val directory = object : ViewModel(), DirectoryUi {
            override val sources = MutableStateFlow<List<SourceEntity>>(emptyList())
            override val sourcePlugins = emptyList<SourcePluginInfo>()
            override val directoryState = MutableStateFlow(DirectoryState(provider = DirectoryProvider.CURATED))
            var searches = 0
            override fun directoryProvider(provider: DirectoryProvider) { directoryState.value = directoryState.value.copy(provider = provider); searchDirectory() }
            override fun directoryQuery(query: String) { directoryState.value = directoryState.value.copy(query = query) }
            override fun searchDirectory() { searches++ }
            override fun hideDirectoryPromotions(value: Boolean) = Unit
            override fun loadMoreDirectory() = Unit
            override fun addDirectorySource(source: DirectorySource) = Unit
        }
        val bindings = FeatureBindings.create(emptyList())
        bindings.controller(FeedFilterUi::class.java) { filter }
        bindings.controller(DirectoryUi::class.java) { directory }
        val host = FeatureHost(ApplicationProvider.getApplicationContext<Application>(), FeatureServices(MemoryNewsStore(), MemorySettings(), bindings = bindings), object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }) { }
        var opened: String? = null
        val nav = object : FeaturePageNavigation {
            override fun hasPage(featureId: String) = featureId == "source-discovery"
            override fun openPage(featureId: String) { opened = featureId }
        }
        try {
            compose.setContent { MaterialTheme { FeedFiltersFeature().Toolbar(host, nav) } }
            compose.onNodeWithTag("open-source-filter").performClick()
            compose.onNodeWithTag("source-filter-search").performTextInput("Kotlin Daily")
            compose.onNodeWithTag("search-online-sources").performClick()
            compose.runOnIdle {
                assertEquals("source-discovery", opened)
                assertEquals("Kotlin Daily", directory.directoryState.value.query)
                assertEquals(DirectoryProvider.RSS, directory.directoryState.value.provider)
                assertEquals(1, directory.searches)
            }
        } finally { host.close() }
    }

}
