package io.newsbrief.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.ui.TestAppModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavigationTest {
    @Test fun nativePublisherReaderReturnsToSavedTelegramPostAndThenFeed() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var vm: TestAppModel? = null
        try {
            val settings = SettingsStore(app).apply {
                markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
                markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
            }
            val post = ArticleEntity(sourceId = 1, sourceName = "dev.by", title = "Post", excerpt = "Post",
                content = "Saved post", readerLoaded = true, link = "https://t.me/devby/16806", publishedAt = 123,
                linksJson = "[{\"label\":\"Подробнее\",\"url\":\"https://devby.io/news/story\"}]")
            val article = post.copy(link = "https://devby.io/news/story", title = "Full article", content = "Saved full article", linksJson = "[]")
            db.articleDao().insertAll(listOf(post, article))
            val repo = TestNewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, settings)
            val model = TestAppModel(app, repo, settings); vm = model
            model.readerUi.openReader(post)
            withTimeout(5000) { model.readerUi.reader.first { it?.text == "Saved post" } }
            model.readerUi.openReaderLink(article.link).join()
            withTimeout(5000) { model.readerUi.reader.first { it?.text == "Saved full article" } }
            assertEquals(article.link, model.readerUi.readerSource.value!!.link)
            model.readerUi.closeReader()
            withTimeout(5000) { model.readerUi.reader.first { it?.text == "Saved post" } }
            assertEquals(post.link, model.readerUi.readerSource.value!!.link)
            model.readerUi.closeReader()
            assertNull(model.readerUi.readerSource.value)
            assertNull(model.readerUi.reader.value)
            assertFalse(model.readerUi.readerBusy.value)
        } finally { vm?.closeFeatures(); db.close(); Dispatchers.resetMain() }
    }
}
