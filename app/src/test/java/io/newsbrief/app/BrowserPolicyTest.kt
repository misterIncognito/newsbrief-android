package io.newsbrief.app

import io.newsbrief.app.network.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BrowserPolicyTest {
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

}
