package io.newsbrief.app

import io.newsbrief.app.network.NewsRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRoutingTest {
    private fun endpoint(url: String, type: String, worker: String, forceProxy: Boolean = false): String =
        io.newsbrief.app.network.PageProxyRegistry(listOf(io.newsbrief.app.network.CloudflarePageProxy()))
            .route(io.newsbrief.app.network.PageRequest(url, type, forceProxy), worker)

    private val worker = "https://newsbrief-proxy.a0-07d.workers.dev"

    @Test fun proxiedNewsIsHttpsAndEncoded() {
        val rss = "https://news.zerkalo.io/rss/all.rss"
        assertEquals("$worker/fetch?url=https%3A%2F%2Fnews.zerkalo.io%2Frss%2Fall.rss",
            endpoint(rss, "fetch", worker))
        assertTrue(endpoint("https://meduza.io/a/story", "article", worker)
            .startsWith("$worker/article?url="))
    }

    @Test fun otherSourcesAreDirectAndSimilarDomainsCannotPretendToBeMeduza() {
        assertEquals("https://www.theverge.com/rss/index.xml",
            endpoint("https://www.theverge.com/rss/index.xml", "fetch", worker))
        assertFalse(NewsRouting.requiresProxy("https://meduza.io.attacker.example/story"))
        assertFalse(NewsRouting.requiresProxy("https://fakezerkalo.io/article"))
    }

    @Test fun blockedArticleImagesMustStayOnWorkerEvenOnExternalCdn() {
        val image = "https://static.example.net/photo.jpg"
        assertEquals("$worker/image?url=https%3A%2F%2Fstatic.example.net%2Fphoto.jpg",
            endpoint(image, "image", worker, forceProxy = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun workerMustUseHttps() {
        endpoint("https://meduza.io/rss/all", "fetch", "http://worker.local")
    }
}
