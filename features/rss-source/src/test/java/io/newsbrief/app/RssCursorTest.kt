package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.RssSourcePlugin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RssCursorTest {
    private val source = SourceEntity(id = 1, name = "Feed", address = "https://example.org/feed", kind = SourceKind.RSS)
    private fun page(ids: List<Int>, next: String? = null) = """<feed xmlns="http://www.w3.org/2005/Atom">
        ${next?.let { "<link rel=\"next\" href=\"$it\"/>" } ?: ""}
        ${ids.joinToString("") { "<entry><title>Post $it</title><link href=\"https://example.org/$it\"/></entry>" }}
        </feed>"""

    @Test fun followsNextPageUntilTheStoredAnchor() = runBlocking {
        val requests = mutableListOf<String>()
        val plugin = RssSourcePlugin { url -> requests += url; if (url.endsWith("/feed")) page(listOf(5, 4), "/older") else page(listOf(3, 2)) }
        val result = plugin.collectSince(source.copy(fetchCursor = "https://example.org/2"))
        assertEquals(listOf("https://example.org/feed", "https://example.org/older"), requests)
        assertEquals(setOf("5", "4", "3", "2"), result.articles.map { it.link.substringAfterLast('/') }.toSet())
        assertEquals("https://example.org/5", result.nextCursor)
        assertFalse(result.historyGap)
    }

    @Test fun storesCurrentItemsButReportsUnavailableHistory() = runBlocking {
        val batch = RssSourcePlugin { page(listOf(5, 4)) }.collectSince(source.copy(fetchCursor = "https://example.org/1"))
        assertEquals(2, batch.articles.size)
        assertNull(batch.nextCursor)
        assertTrue(batch.historyGap)
    }

    @Test fun doesNotFollowNextLinksToAnotherOrigin() = runBlocking {
        val requests = mutableListOf<String>()
        val plugin = RssSourcePlugin { url -> requests += url; page(listOf(5), "https://other.example/next") }
        val result = plugin.collectSince(source.copy(fetchCursor = "https://example.org/1"))
        assertTrue(result.historyGap)
        assertEquals(listOf(source.address), requests)
    }

    @Test fun collectsMoreThanTheOldParserLimit() = runBlocking {
        val result = RssSourcePlugin { page((1..150).toList()) }.collectSince(source)
        assertEquals(150, result.articles.size)
    }
    @Test fun keepsPostsWithGuidOrRelativeUrlEvenWhenTitleIsMissing() = runBlocking {
        val xml = """<rss><channel>
            <item><guid>https://example.org/by-guid</guid><description>Details</description></item>
            <item><title>Relative</title><link>/relative</link></item>
        </channel></rss>"""
        val result = RssSourcePlugin { xml }.collectSince(source)
        assertEquals(setOf("https://example.org/by-guid", "https://example.org/relative"), result.articles.map { it.link }.toSet())
        assertEquals("Feed", result.articles.first().title)
    }

}
