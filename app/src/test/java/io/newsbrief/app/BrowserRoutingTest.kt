package io.newsbrief.app

import io.newsbrief.app.network.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BrowserRoutingTest {
    @Test fun protectedPagesAndTheirEmbeddedRequestsUseWorker() {
        val document = "https://zerkalo-6oct.site/news/life/138079.html"
        assertTrue(BrowserRouting.route(document, true, "").forceProxy)
        assertTrue(BrowserRouting.route("https://cdn.example/style.css", false, document).forceProxy)
        assertTrue(BrowserRouting.route("https://meduza.io/story", true, "https://devby.io").forceProxy)
        assertFalse(BrowserRouting.route("https://devby.io/news/story", true, document).forceProxy)
    }
    @Test fun browserImagesUseImageEndpointEvenForOtherPublishers() {
        val image = BrowserRouting.route("https://cdn.example/photo.jpg?size=2", false, "https://devby.io")
        assertEquals("image", image.type)
        assertTrue(image.forceProxy)
        assertEquals("image", BrowserRouting.route("https://cdn.example/asset", false, "", "image/avif,image/webp").type)
    }
    @Test fun unsafeBrowserRequestsAreRejectedBeforeNetworking() {
        for (url in listOf("http://news.zerkalo.io/story", "file:///private/file", "intent://open", "https://user:pass@example.org")) {
            assertTrue(runCatching { BrowserRouting.route(url, true, "") }.isFailure)
        }
    }
    @Test fun browserTransportPreservesMimeEncodingAndRoutesRedirects() {
        val requests = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Test")
                .apply {
                    if (requests.size == 1) code(302).header("Location", "https://news.zerkalo.io/story")
                    else code(200)
                }.body("<p>Story</p>".toResponseBody("text/html; charset=utf-8".toMediaType())).build()
        }.build()
        val result = NewsHttp.downloadResource("https://example.org/redirect", "fetch", NewsRouting.DEFAULT_WORKER, 1000, transport = transport)
        assertEquals("text/html", result.mimeType)
        assertEquals("UTF-8", result.encoding)
        assertEquals("https://news.zerkalo.io/story", result.finalUrl)
        assertTrue(requests[1].startsWith(NewsRouting.DEFAULT_WORKER + "/fetch?"))
    }
    @Test fun failedProtectedEmbeddedResourceNeverFallsBackToDirect() {
        val requests = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(502).message("Unavailable").body("Blocked".toResponseBody()).build()
        }.build()
        val url = "https://cdn.example/style.css"
        val route = BrowserRouting.route(url, false, "https://zerkalo-6oct.site/news/story")
        assertTrue(runCatching { NewsHttp.downloadResource(url, route.type, NewsRouting.DEFAULT_WORKER, 1000, route.forceProxy, transport) }.isFailure)
        assertEquals(1, requests.size)
        assertTrue(requests.single().startsWith(NewsRouting.DEFAULT_WORKER + "/fetch?"))
    }
}
