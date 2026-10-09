package io.newsbrief.app

import io.newsbrief.app.network.ArticleReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ArticleReaderTest {
    @Test fun workerContractPreservesParagraphsDateAndImages() {
        val result = ArticleReader.parseArticle("""{
            "title":"A &amp; B", "text":"Flattened fallback", "paragraphs":["First paragraph.","Second paragraph."],
            "author":"Reporter", "publishedAt":"2026-10-08T12:00:00Z",
            "imageUrl":"https://cdn.example/image.jpg", "imageProxyUrl":"https://worker.example/image?url=x",
            "images":["https://cdn.example/second.jpg"], "extraction":"readability"
        }""", "https://meduza.io/story")
        assertEquals("A & B", result.title)
        assertEquals("First paragraph.\n\nSecond paragraph.", result.text)
        assertEquals(1791460800000L, result.publishedAt)
        assertEquals("Reporter", result.author)
        assertEquals(2, result.images.size)
    }
    @Test fun nullableMetadataAndNestedHtmlAreSupported() {
        val result = ArticleReader.parseArticle("""{"article":{"title":"Story","publishedAt":null,
            "content":"<p>First.</p><p>Second.</p>","image":{"url":"https://example.org/photo.png"}}}""",
            "https://example.org/story")
        assertEquals("First.\n\nSecond.", result.text)
        assertNull(result.publishedAt)
        assertEquals("https://example.org/photo.png", result.imageUrl)
    }
    @Test fun directHtmlResolvesImagesAndRemovesScripts() {
        val result = ArticleReader.parseArticle("""<html><title>Title</title><main><h1>Headline</h1>
            <p>Text.</p><img src="/image.png"><script>secret()</script></main></html>""", "https://example.org/story")
        assertEquals("Headline", result.title)
        assertFalse(result.text.contains("secret"))
        assertEquals("https://example.org/image.png", result.imageUrl)
    }
    @Test fun savedShortArticleOpensOfflineWithoutFetching() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val article = io.newsbrief.app.data.ArticleEntity(sourceId = 1, sourceName = "Source", title = "Short",
            excerpt = "Summary", content = "Saved short text.", readerLoaded = true,
            link = "https://meduza.io/story", publishedAt = 123, author = "Author")
        val result = ArticleReader(io.newsbrief.app.data.SettingsStore(context)).load(article)
        assertEquals("Saved short text.", result.text)
        assertNull(result.notice)
        assertEquals("Author", result.author)
    }
}
