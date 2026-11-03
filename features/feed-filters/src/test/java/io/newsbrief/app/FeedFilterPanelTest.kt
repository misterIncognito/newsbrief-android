package io.newsbrief.app

import android.app.Application
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

    private class FakeFilter : FeedFilterUi {
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
}
