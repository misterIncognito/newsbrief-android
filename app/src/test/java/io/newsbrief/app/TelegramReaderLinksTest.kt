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
class TelegramReaderLinksTest {
    private val settings get() = SettingsStore(ApplicationProvider.getApplicationContext())
    private val post = ArticleEntity(sourceId = 1, sourceName = "dev.by", title = "Saved",
        excerpt = "Preview", content = "Saved offline text", readerLoaded = true,
        link = "https://t.me/devby/16806", publishedAt = 123)
    private val preview = """<div class="tgme_widget_message" data-post="devby/16805">
        <div class="tgme_widget_message_text">Wrong neighbour<a href="https://devby.io/news/wrong">Подробнее</a></div></div>
        <div class="tgme_widget_message" data-post="devby/16806">
        <div class="tgme_widget_message_text">New post<br><a href="https://devby.io/news/right">Подробнее</a>
        <a href="javascript:alert(1)">Unsafe</a></div><time datetime="2026-10-08T12:00:00Z"></time></div>"""

    @Test fun collectorRetainsLabelsTargetsAndExactPostIds() {
        val items = WebCollectors().parseTelegramPreview(preview,
            SourceEntity(name = "dev.by", address = "https://t.me/s/devby", kind = SourceKind.TELEGRAM))
        val correct = items.single { it.link == post.link }
        val link = ArticleLinks.decode(correct.linksJson).single()
        assertEquals("Подробнее", link.label)
        assertEquals("https://devby.io/news/right", link.url)
        assertTrue(correct.content.contains("Подробнее"))
    }
    @Test fun oldSavedTelegramPostHydratesExactPostWithoutReplacingOfflineText() = runBlocking {
        var calls = 0
        val reader = ArticleReader(settings) { url, type, _, _ ->
            calls++
            assertEquals("https://t.me/s/devby/16806", url)
            assertEquals("fetch", type)
            preview.toByteArray()
        }
        val result = reader.load(post)
        assertEquals(1, calls)
        assertEquals("Saved offline text", result.text)
        assertEquals("https://devby.io/news/right", result.links.single().url)
        assertNull(result.notice)
    }
    @Test fun hydratedPostAndLinksOpenOfflineWithoutNetwork() = runBlocking {
        val links = listOf(ArticleLink("Подробнее", "https://devby.io/news/right"))
        val result = ArticleReader(settings) { _, _, _, _ -> error("Must not fetch") }
            .load(post.copy(linksJson = ArticleLinks.encode(links)))
        assertEquals(links, result.links)
        assertEquals(post.content, result.text)
        assertNull(result.notice)
    }
    @Test fun unavailableTelegramPostKeepsOfflineBodyAndNeverUsesNeighbour() = runBlocking {
        val result = ArticleReader(settings) { _, _, _, _ ->
            preview.substringBefore("<div class=\"tgme_widget_message\" data-post=\"devby/16806\"").toByteArray()
        }.load(post)
        assertEquals(post.content, result.text)
        assertTrue(result.links.isEmpty())
        assertNotNull(result.notice)
    }
    @Test fun refreshingHydratedPostActuallyFetches() = runBlocking {
        var calls = 0
        val result = ArticleReader(settings) { _, _, _, _ -> calls++; preview.toByteArray() }
            .load(post.copy(linksJson = "[{\"label\":\"Old\",\"url\":\"https://devby.io/news/old\"}]"), true)
        assertEquals(1, calls)
        assertEquals("https://devby.io/news/right", result.links.single().url)
    }
    @Test fun zerkaloPublisherMirrorRemainsStrictlyProxied() {
        val url = "https://zerkalo-6oct.site/news/economics/138172.html?tg=6"
        assertTrue(NewsRouting.requiresProxy(url))
        assertTrue(NewsRouting.endpoint(url, "article", NewsRouting.DEFAULT_WORKER).startsWith(NewsRouting.DEFAULT_WORKER + "/article?url="))
        assertFalse(NewsRouting.requiresProxy("https://zerkalo-6oct.site.evil.example/news"))
        assertFalse(NewsRouting.requiresProxy("https://notzerkalo-6oct.site/news"))
        val links = listOf(ArticleLink("Telegram", "https://t.me/another/2"), ArticleLink("Читать", url))
        assertEquals(url, ArticleLinks.fullArticle(links)!!.url)
    }
    @Test fun malformedStoredLinksAndUnsafeSchemesAreIgnored() {
        assertTrue(ArticleLinks.decode("broken").isEmpty())
        assertTrue(ArticleLinks.decode("[{\"url\":\"javascript:alert(1)\"}]").isEmpty())
        assertFalse(ArticleLinks.valid("https://user:pass@example.org/story"))
        assertNull(ArticleLinks.telegramPreview("https://t.me/devby"))
        assertNull(ArticleLinks.telegramPreview("https://t.me.evil.example/devby/1"))
    }
    @Test fun devbyReaderUsesArticleBodyAndRetainsRelatedLinks() {
        val result = ArticleReader.parseArticle("""<article><h1>Story</h1><div class="article__header">MENU</div>
            <div class="article__body"><p>Full text</p><a href="/news/related">Related</a></div>
            <div class="article__footer">ADVERT</div></article>""", "https://devby.io/news/story")
        assertTrue(result.text.contains("Full text"))
        assertFalse(result.text.contains("MENU"))
        assertFalse(result.text.contains("ADVERT"))
        assertEquals("https://devby.io/news/related", result.links.single().url)
    }
}
