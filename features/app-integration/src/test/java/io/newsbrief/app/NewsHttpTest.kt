package io.newsbrief.app

import io.newsbrief.app.network.NewsHttp
import io.newsbrief.app.network.NewsRouting
import io.newsbrief.app.network.PageProxyRegistry
import io.newsbrief.app.network.CloudflarePageProxy
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NewsHttpTest {
    private val router = PageProxyRegistry(listOf(CloudflarePageProxy()))
    @Test fun proxyFailureNeverFallsBackToDirect() {
        val requests = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(502).message("Upstream failure").body("Failure".toResponseBody()).build()
        }.build()
        assertTrue(runCatching { NewsHttp.download("https://meduza.io/story", "article",
            NewsRouting.DEFAULT_WORKER, 1000, transport = transport, router = router) }.isFailure)
        assertEquals(1, requests.size)
        assertTrue(requests.single().startsWith(NewsRouting.DEFAULT_WORKER + "/article?"))
    }
    @Test fun directRedirectToRequiredSourceGoesThroughWorker() {
        val requests = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .message("Test").body("feed".toResponseBody())
            if (requests.size == 1) builder.code(302).header("Location", "https://news.zerkalo.io/rss/all.rss")
            else builder.code(200)
            builder.build()
        }.build()
        assertEquals("feed", NewsHttp.download("https://example.org/feed", "fetch",
            NewsRouting.DEFAULT_WORKER, 1000, transport = transport, router = router).toString(Charsets.UTF_8))
        assertEquals(2, requests.size)
        assertTrue(requests[1].startsWith(NewsRouting.DEFAULT_WORKER + "/fetch?"))
    }
    @Test fun workerRedirectCannotEscapeToSource() {
        var requests = 0
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(302).message("Redirect").header("Location", "https://meduza.io/story")
                .body("".toResponseBody()).build()
        }.build()
        assertTrue(runCatching { NewsHttp.download("https://meduza.io/story", "article",
            NewsRouting.DEFAULT_WORKER, 1000, transport = transport, router = router) }.isFailure)
        assertEquals(1, requests)
    }
}
