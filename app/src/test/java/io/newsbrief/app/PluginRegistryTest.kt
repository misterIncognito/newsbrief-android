package io.newsbrief.app

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
class PluginRegistryTest {
    private val settings get() = SettingsStore(ApplicationProvider.getApplicationContext<Application>())
    private fun article(url: String = "https://publisher.example/story", proxy: Boolean = false) = ArticleEntity(
        sourceId = 1, sourceName = "Publisher", title = "Cached", excerpt = "Cached text", link = url, publishedAt = 123,
        proxyRequired = proxy)

    @Test fun customSourceRegistersWithoutChangingCollectorAndUnknownIdsNeverFetch() = runBlocking {
        var calls = 0
        val plugin = object : NewsSourcePlugin {
            override val info = SourcePluginInfo("CUSTOM", "Custom source", "Address")
            override fun canonicalAddress(input: String) = "custom:${input.trim()}"
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> { calls++; return listOf(article()) }
        }
        val registry = SourcePluginRegistry(listOf(plugin))
        val source = SourceEntity(name = "Custom", kind = "CUSTOM", address = registry.canonicalAddress(" x ", "CUSTOM"))
        assertEquals("custom:x", source.address)
        assertEquals("CUSTOM", registry.collect(source).single().sourcePluginId)
        assertTrue(runCatching { registry.collect(source.copy(kind = "MISSING")) }.isFailure)
        assertEquals(1, calls)
        assertTrue(runCatching { SourcePluginRegistry(listOf(plugin, plugin)) }.isFailure)
    }
    @Test fun builtInSourcePluginsKeepRssAtomAndTelegramContent() = runBlocking {
        val rss = RssSourcePlugin { """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Story</title><link href="https://example.org/story"/><content type="html">&lt;p&gt;Full content&lt;/p&gt;</content></entry></feed>""" }
        val tg = TelegramPreviewSourcePlugin { """<div class="tgme_widget_message" data-post="channel/1"><div class="tgme_widget_message_text">Post <a href="https://publisher.example/story">details</a></div></div>""" }
        val registry = SourcePluginRegistry(listOf(rss, tg))
        assertEquals("Full content", registry.collect(SourceEntity(name = "RSS", kind = SourceKind.RSS, address = "https://example.org/rss")).single().content)
        val post = registry.collect(SourceEntity(name = "Telegram", kind = SourceKind.TELEGRAM, address = "https://t.me/s/channel")).single()
        assertEquals("details", ArticleLinks.decode(post.linksJson).single().label)
        assertEquals(SourceKind.TELEGRAM, post.sourcePluginId)
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
    @Test fun telegramProxyFailureNeverFallsBackToPublicPreviewOrPublisher() = runBlocking {
        val reader = ArticleReader(settings,
            telegram = TelegramProxyClient { TelegramProxyConfig("", "") },
            proxyDownload = { _, _, _, _ -> error("No Worker fallback for proxy post") },
            download = { _, _, _, _ -> error("No public Telegram fallback") })
        val post = article("https://t.me/channel/10").copy(sourcePluginId = SourceKind.TELEGRAM_PROXY)
        val result = reader.load(post, refresh = true)
        assertNotNull(result.notice); assertEquals("Cached text", result.text)
        val offline = post.copy(content = "Offline post")
        assertNull(reader.load(offline).notice)
        assertEquals("Offline post", reader.load(offline).text)
    }
}
