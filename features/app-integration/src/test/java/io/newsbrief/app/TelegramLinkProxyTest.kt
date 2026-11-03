package io.newsbrief.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.TestAppModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TelegramLinkProxyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val post get() = ArticleEntity(sourceId = 1, sourceName = "Channel", title = "Post", excerpt = "Post",
        content = "Post text", link = "https://t.me/channel/1", publishedAt = 123)

    @Test fun feedLinkUsesProxyAndBackReturnsStraightToFeedWithSavedRoute() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var vm: TestAppModel? = null
        try {
            val settings = SettingsStore(app).apply {
                markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
                markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
            }
            val url = "https://devby.io/news/story"
            var proxied = 0
            val reader = ArticleReader(settings, proxyDownload = { target, type, _, _ ->
                assertEquals(url, target); assertEquals("fetch", type); proxied++
                """{"title":"Full article","text":"Full body"}""".toByteArray()
            }, download = { _, _, _, _ -> error("Direct request must not happen") })
            val repo = TestNewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, settings, articleReader = reader)
            val model = TestAppModel(app, repo, settings); vm = model
            model.readerUi.openFeedLink(post, url).join()
            withTimeout(5000) { model.readerUi.reader.first { it?.text == "Full body" } }
            assertEquals(1, proxied)
            assertEquals(url, model.readerUi.readerSource.value!!.link)
            assertTrue(model.readerUi.readerSource.value!!.proxyRequired)
            assertTrue(repo.lookup(url)!!.proxyRequired)
            model.browser.openBrowser(url, model.readerUi.readerSource.value!!.proxyRequired)
            assertTrue(model.browser.browserForceProxy.value)
            assertEquals(url, model.browser.browserUrl.value)
            assertTrue(BrowserRouting.route(url, true, url, forceDocumentProxy = model.browser.browserForceProxy.value).forceProxy)
            model.browser.closeBrowser(); model.readerUi.closeReader()
            assertNull(model.readerUi.readerSource.value)
            val offline = repo.readArticle(repo.lookup(url)!!)
            assertEquals("Full body", offline.text)
            assertEquals(1, proxied)
            val oldDirect = post.copy(link = "https://other.example/cached", content = "Earlier direct cache", proxyRequired = false)
            db.articleDao().insertAll(listOf(oldDirect))
            model.readerUi.openFeedLink(post, oldDirect.link).join()
            withTimeout(5000) { model.readerUi.reader.first { it?.text == "Earlier direct cache" } }
            assertTrue(repo.lookup(oldDirect.link)!!.proxyRequired)
            assertEquals(1, proxied)
        } finally { vm?.closeFeatures(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun failedTelegramArticleProxyDoesNotUseDirectTransport() = runBlocking {
        var calls = 0
        val reader = ArticleReader(SettingsStore(app), proxyDownload = { _, _, _, _ -> calls++; error("Worker unavailable") },
            download = { _, _, _, _ -> error("Must not connect directly") })
        val result = reader.load(post.copy(link = "https://devby.io/news/story", content = "", proxyRequired = true))
        assertEquals(2, calls)
        assertNotNull(result.notice)
        assertTrue(result.notice!!.contains("Worker unavailable"))
    }
    @Test fun inheritedBrowserProxyCoversUnknownPublisherAndAllResources() {
        for (mainFrame in listOf(true, false)) {
            val route = BrowserRouting.route("https://unknown.example/page", mainFrame, "https://devby.io/story", forceDocumentProxy = true)
            assertTrue(route.forceProxy)
            assertTrue(NewsRouting.endpoint("https://unknown.example/page", route.type, NewsRouting.DEFAULT_WORKER, route.forceProxy)
                .startsWith(NewsRouting.DEFAULT_WORKER + "/fetch?"))
        }
        assertFalse(BrowserRouting.route("https://unknown.example/page", true, "").forceProxy)
    }
}
