package io.newsbrief.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.newsbrief.app.data.*
import io.newsbrief.app.features.SaveArticlesFeature
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.ui.SaveArticleButton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = android.app.Application::class)
class SaveArticleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun saveFeatureRegistersAnActionAndPersistsThroughStorePort() = runBlocking {
        val bindings = FeatureBindings.create(listOf(SaveArticlesFeature()))
        val store = MemoryNewsStore()
        val item = ArticleEntity(sourceId = 1, sourceName = "Source", title = "Title", excerpt = "Preview", link = "https://example.org/story", publishedAt = 1)
        assertEquals(1, bindings.articleActions.size)
        assertTrue(bindings.saveArticle!!.invoke(store, item))
        assertEquals(item.link, store.articleDao().observeSaved().first().single().link)
        assertFalse(bindings.saveArticle!!.invoke(store, item))
        assertTrue(store.articleDao().observeSaved().first().isEmpty())
    }
    @Test fun saveButtonTogglesWithoutShareFeature() {
        var saved by mutableStateOf(false)
        compose.setContent { MaterialTheme { SaveArticleButton(saved) { saved = !saved } } }
        compose.onNodeWithText("Поделиться").assertDoesNotExist()
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("✓ Сохранено").assertExists().performClick()
        compose.onNodeWithText("Сохранить").assertExists()
    }
}
