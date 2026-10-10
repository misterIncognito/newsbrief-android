package io.newsbrief.app

import io.newsbrief.app.TestSettings as SettingsStore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArticlePluginRegistryTest {
    private val settings get() = SettingsStore(ApplicationProvider.getApplicationContext<Application>())
    private fun article(url: String = "https://publisher.example/story", proxy: Boolean = false) = ArticleEntity(
        sourceId = 1, sourceName = "Publisher", title = "Cached", excerpt = "Cached text", link = url, publishedAt = 123,
        proxyRequired = proxy)

    @Test fun customArticlePluginCanPrecedeBuiltInsWithoutChangingReader() = runBlocking {
        val plugin = object : ArticleFetchPlugin {
            override val id = "custom-reader"
            override fun supports(request: ArticleFetchRequest) = request.article.link.contains("publisher.example")
            override suspend fun fetch(request: ArticleFetchRequest) = ReaderArticle("Custom", "Plugin content", "", request.article.link)
        }
        val registry = ArticleFetchRegistry(listOf(plugin) + ArticleFetchRegistry.builtIns().plugins)
        val reader = ArticleReader(settings, plugins = registry, download = { _, _, _, _ -> error("Unexpected network") })
        assertEquals("Plugin content", reader.load(article()).text)
        assertTrue(runCatching { ArticleFetchRegistry(listOf(plugin, plugin)) }.isFailure)
    }
    @Test fun declinedArticlePluginContinuesAndTerminalFailureDoesNot() = runBlocking {
        val calls = mutableListOf<String>()
        fun plugin(id: String, fail: Boolean = false, decline: Boolean = false) = object : ArticleFetchPlugin {
            override val id = id
            override fun supports(request: ArticleFetchRequest) = true
            override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle? {
                calls += id
                if (fail) error("Unavailable")
                return if (decline) null else ReaderArticle(id, "Article", "", request.article.link)
            }
        }
        assertEquals("next", ArticleReader(settings, plugins = ArticleFetchRegistry(listOf(plugin("decline", decline = true), plugin("next")))).load(article()).title)
        calls.clear()
        val result = ArticleReader(settings, plugins = ArticleFetchRegistry(listOf(plugin("terminal", fail = true), plugin("forbidden-fallback")))).load(article())
        assertNotNull(result.notice); assertEquals(listOf("terminal"), calls)
    }
    @Test fun articleTransportKeepsProxyPolicyAcrossCustomAmpHost() = runBlocking {
        val requests = mutableListOf<String>()
        val reader = ArticleReader(settings, proxyDownload = { url, _, _, _ ->
            requests += url
            if (requests.size == 1) """<link rel="amphtml" href="https://another.example/amp">""".toByteArray()
            else """<html amp><article><p>Full AMP text</p></article></html>""".toByteArray()
        }, download = { _, _, _, _ -> error("Direct connection forbidden") })
        assertEquals("Full AMP text", reader.load(article(proxy = true)).text)
        assertEquals(listOf("https://publisher.example/story", "https://another.example/amp"), requests)
    }

}
