package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AmpArticleTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val settings get() = SettingsStore(app)
    private val url = "https://publisher.example/news/story"
    private val ampUrl = "https://publisher.example/news/story/amp"
    private val original = """<html><head><link rel="amphtml" href="/news/story/amp">
        <meta name="author" content="Original author"></head><body><article><h1>Original</h1>
        <p>Original full article.</p></article></body></html>"""
    private val amp = """<html amp><head><script type="application/ld+json">{"@graph":[
        {"@type":"WebSite","name":"Publisher"},{"@type":["Article","NewsArticle"],
        "headline":"AMP headline","author":[{"name":"AMP reporter"}],
        "datePublished":"2026-10-08T12:00:00Z","image":{"url":"https://cdn.example/cover.jpg"}}]}</script>
        </head><body><nav>MENU</nav><article><div itemprop="articleBody"><p>Full simplified article.</p>
        <amp-img src="/photos/inside.jpg"></amp-img><amp-ad><p>ADVERT</p></amp-ad>
        <amp-analytics>TRACKING</amp-analytics><a href="/news/related">Related</a></div></article></body></html>"""
    private fun article(link: String = url, proxy: Boolean = false) = ArticleEntity(sourceId = 1,
        sourceName = "Publisher", title = "Preview", excerpt = "Summary", link = link,
        publishedAt = System.currentTimeMillis() - 1000, proxyRequired = proxy)

    @Test fun discoversRelativeAmpWithBaseAndCaseInsensitiveRelationTokens() {
        assertEquals("https://cdn.example/light/story", AmpDiscovery.alternative("""<head>
            <base href="https://cdn.example/light/"><link rel="alternate AMPHTML" href="story#top"></head>""", url))
    }
    @Test fun rejectsUnsafeMalformedAndSelfAlternativesButFindsNextValidLink() {
        for (target in listOf("http://example.org/amp", "javascript:alert(1)", "https://user:pass@example.org/amp", "https://[broken", "$url#top")) {
            assertNull(target, AmpDiscovery.alternative("""<head><link rel="amphtml" href="$target"></head>""", url))
        }
        assertEquals(ampUrl, AmpDiscovery.alternative("""<head><link rel="amphtml" href="javascript:x">
            <link rel="amphtml" href="$ampUrl"></head>""", url))
    }
    @Test fun alreadyAmpAndMissingAlternativesDoNotLoopOrGuessUrls() {
        for (attribute in listOf("amp", "⚡")) assertNull(AmpDiscovery.alternative(
            """<html $attribute><head><link rel="amphtml" href="/another"></head></html>""", url))
        assertNull(AmpDiscovery.alternative("<article><p>No alternative</p></article>", url))
    }
    @Test fun ampParserKeepsStructuredMetadataImagesAndLinksAndRemovesClutter() {
        val reader = ArticleReader.parseArticle(amp, ampUrl)
        assertEquals("AMP headline", reader.title)
        assertEquals("AMP reporter", reader.author)
        assertEquals(1791460800000L, reader.publishedAt)
        assertEquals("https://cdn.example/cover.jpg", reader.images.last())
        assertTrue(reader.images.contains("https://publisher.example/photos/inside.jpg"))
        assertTrue(reader.text.contains("Full simplified article."))
        for (clutter in listOf("MENU", "ADVERT", "TRACKING")) assertFalse(reader.text.contains(clutter))
        assertEquals("https://publisher.example/news/related", reader.links.single().url)
    }
    @Test fun zerkaloStyleAmpUsesArticleContainerAndValidSchemaDateAfterMalformedMeta() {
        val html = amp.replace("<article><div itemprop=\"articleBody\">", "<div class=\"b-article\"><div>")
            .replace("</div></article>", "<div class=\"b-related\"><p>RELATED PROMOTION</p></div></div></div>")
            .replace("</head>", "<meta property=\"article:published_time\" content=\"2026-10-08T23:26+0300Z\"></head>")
        val reader = ArticleReader.parseArticle(html, ampUrl)
        assertEquals(1791460800000L, reader.publishedAt)
        assertFalse(reader.text.contains("MENU"))
        assertFalse(reader.text.contains("RELATED PROMOTION"))
        assertTrue(reader.text.contains("Full simplified article."))
    }
    @Test fun directReaderPrefersAdvertisedAmpAndKeepsOriginalArticleIdentity() = runBlocking {
        val requests = mutableListOf<String>()
        val reader = ArticleReader(settings, download = { target, type, _, _ ->
            assertEquals("fetch", type); requests += target
            (if (target == url) original else amp).toByteArray()
        }).load(article())
        assertEquals(listOf(url, ampUrl), requests)
        assertEquals(url, reader.originalUrl)
        assertEquals("AMP headline", reader.title)
        assertNull(reader.notice)
    }
    @Test fun failedOrEmptyAmpFallsBackToOriginalTextAndMetadata() = runBlocking {
        for (response in listOf<String?>(null, "<html amp><body>No article</body></html>")) {
            val reader = ArticleReader(settings, download = { target, _, _, _ ->
                if (target == url) original.toByteArray() else response?.toByteArray() ?: error("AMP unavailable")
            }).load(article())
            assertTrue(reader.text.contains("Original full article."))
            assertEquals("Original author", reader.author)
            assertNull(reader.notice)
        }
    }
    @Test fun missingAmpMetadataFallsBackToPublisherMetadata() = runBlocking {
        val reader = ArticleReader(settings, download = { target, _, _, _ ->
            (if (target == url) original else "<html amp><body><article><p>AMP body</p></article></body></html>").toByteArray()
        }).load(article())
        assertEquals("Original", reader.title)
        assertEquals("Original author", reader.author)
        assertEquals("AMP body", reader.text)
    }
    @Test fun malformedOriginalMetadataDoesNotPreventUsingValidAmp() = runBlocking {
        val malformed = original.replace("</head>", "<meta property=\"og:image\" content=\"https://[broken\"></head>")
        val reader = ArticleReader(settings, download = { target, _, _, _ ->
            (if (target == url) malformed else amp).toByteArray()
        }).load(article())
        assertEquals("AMP headline", reader.title)
        assertTrue(reader.text.contains("Full simplified article."))
        assertNull(reader.notice)
    }
    @Test fun emptyStructuredImageDoesNotTurnArticleUrlIntoAnImage() {
        val html = """<html amp><head><script type="application/ld+json">{"@type":"NewsArticle",
            "headline":"Story","image":{"@type":"ImageObject"}}</script></head>
            <body><article><p>Text</p></article></body></html>"""
        val reader = ArticleReader.parseArticle(html, ampUrl)
        assertTrue(reader.images.isEmpty())
        assertEquals("", reader.imageUrl)
    }
    @Test fun articlePhotosAndStructuredCoverPrecedeGenericSocialBanner() {
        val html = amp.replace("</head>", "<meta property=\"og:image\" content=\"/generic-share.png\"></head>")
        val withPhoto = ArticleReader.parseArticle(html, ampUrl)
        assertEquals("https://publisher.example/photos/inside.jpg", withPhoto.imageUrl)
        val withoutPhoto = ArticleReader.parseArticle(html.replace("<amp-img src=\"/photos/inside.jpg\"></amp-img>", ""), ampUrl)
        assertEquals("https://cdn.example/cover.jpg", withoutPhoto.imageUrl)
        assertEquals("https://publisher.example/generic-share.png", withoutPhoto.images.last())
    }
    @Test fun protectedPublisherKeepsDifferentHostAmpOnProxy() = runBlocking {
        val protected = "https://meduza.io/news/story"
        val externalAmp = "https://amp.publisher-cdn.example/story"
        val requests = mutableListOf<String>()
        val reader = ArticleReader(settings, proxyDownload = { target, type, _, _ ->
            assertEquals("fetch", type); requests += target
            (if (target == protected) original.replace("/news/story/amp", externalAmp) else amp).toByteArray()
        }, download = { _, _, _, _ -> error("Direct request forbidden") }).load(article(protected))
        assertEquals(listOf(protected, externalAmp), requests)
        assertEquals(protected, reader.originalUrl)
        assertNull(reader.notice)
    }
    @Test fun failedTelegramAmpUsesWorkerExtractionWithoutDirectFallback() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val reader = ArticleReader(settings, proxyDownload = { target, type, _, _ ->
            requests += target to type
            when {
                target == ampUrl -> error("AMP blocked")
                type == "fetch" -> original.toByteArray()
                else -> """{"title":"Worker title","text":"Worker full text"}""".toByteArray()
            }
        }, download = { _, _, _, _ -> error("Direct request forbidden") }).load(article(proxy = true))
        assertEquals(listOf(url to "fetch", ampUrl to "fetch", url to "article"), requests)
        assertEquals("Worker full text", reader.text)
        assertEquals("Original author", reader.author)
        assertNull(reader.notice)
    }
    @Test fun failedDiscoveryStillUsesWorkerExtractionAndTotalFailureKeepsOfflineText() = runBlocking {
        val reader = ArticleReader(settings, proxyDownload = { _, type, _, _ ->
            if (type == "fetch") error("HTML unavailable")
            """{"text":"Extracted text"}""".toByteArray()
        }, download = { _, _, _, _ -> error("Direct request forbidden") }).load(article(proxy = true))
        assertEquals("Extracted text", reader.text)
        val cached = article(proxy = true).copy(content = "Saved body", readerLoaded = true)
        val failure = ArticleReader(settings, proxyDownload = { _, _, _, _ -> error("Worker unavailable") },
            download = { _, _, _, _ -> error("Direct request forbidden") }).load(cached, refresh = true)
        assertEquals("Saved body", failure.text)
        assertNotNull(failure.notice)
    }
    @Test fun ampCancellationPropagatesWithoutFallbackRequest() = runBlocking {
        var requests = 0
        try {
            ArticleReader(settings, download = { _, _, _, _ ->
                if (++requests == 1) original.toByteArray() else throw CancellationException("Stopped")
            }).load(article())
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(2, requests) }
    }
    @Test fun telegramAmpPrefetchIsSavedUnderPublisherUrlAndReopensOffline() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val id = db.sourceDao().insert(SourceEntity(name = "Channel", address = "https://t.me/s/channel", kind = SourceKind.TELEGRAM))
            val post = article("https://t.me/channel/1").copy(sourceId = id, content = "Full Telegram post",
                linksJson = ArticleLinks.encode(listOf(ArticleLink("Read", url))))
            db.articleDao().insertAll(listOf(post))
            var requests = 0
            val reader = ArticleReader(settings, proxyDownload = { target, type, _, _ ->
                requests++; assertEquals("fetch", type)
                (if (target == url) original else amp).toByteArray()
            }, download = { _, _, _, _ -> error("Direct request forbidden") })
            val repo = TestNewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, settings, articleReader = reader)
            val images = mutableSetOf<String>()
            val imageCache = object : PrefetchImageStore {
                override fun contains(url: String) = url in images
                override suspend fun load(url: String, worker: String): ByteArray { images += url; return byteArrayOf(1) }
            }
            val result = ArticlePrefetcher(repo, settings, imageCache).prefetch()
            assertEquals(1, result.articles); assertEquals(1, result.images); assertEquals(0, result.failures)
            val saved = repo.lookup(url)!!
            assertTrue(saved.proxyRequired); assertTrue(saved.readerLoaded)
            assertNull(repo.lookup(ampUrl))
            assertEquals(post.content, repo.lookup(post.link)!!.content)
            assertEquals("AMP headline", repo.readArticle(saved).title)
            assertTrue(repo.readArticle(saved).text.contains("Full simplified article."))
            assertTrue(images.contains(saved.imageUrl))
            assertEquals(2, requests)
        } finally { db.close() }
    }
}
