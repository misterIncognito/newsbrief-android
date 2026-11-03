package io.newsbrief.app

import io.newsbrief.app.network.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import io.newsbrief.app.features.NetworkingFeature
import io.newsbrief.app.runtime.FeatureBindings

class NewsHttpDirectTest {
    @Test fun configuredTransportUsesRouterFromItsOwnComposition() {
        val bindings = FeatureBindings.create(listOf(NetworkingFeature()))
        var routed = false
        bindings.pageRouter = { object : PageRouter {
            override fun route(request: PageRequest, configuration: String): String {
                routed = true
                throw IllegalStateException("router reached")
            }
        } }
        val error = assertThrows(IllegalStateException::class.java) {
            bindings.http!!.resource(PageRequest("https://example.org/feed", "fetch", false), "", 100)
        }
        assertEquals("router reached", error.message)
        assertTrue(routed)
    }
    @Test fun preferredProxyRedirectNeverBecomesADirectImageRequest() {
        var calls = 0
        val proxy = object : PageRouter {
            override fun route(request: PageRequest, configuration: String) = "https://proxy.example/image"
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
                .header("Location", "https://cdn.example/image.png").body("".toResponseBody()).build()
        }.build()
        assertTrue(runCatching { NewsHttp.download("https://cdn.example/image.png", "image", "", 100,
            transport = client, router = proxy, preferProxy = true) }.isFailure)
        assertEquals(1, calls)
    }
    @Test fun ordinaryFeedLoadsWithoutPageProxyAndRequiredSourcesNeverConnectDirectly() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("rss".toResponseBody()).build()
        }.build()
        assertEquals("rss", NewsHttp.download("https://example.org/rss", "fetch", "", 100, transport = client, router = DirectPageRouter).decodeToString())
        assertTrue(runCatching { NewsHttp.download("https://meduza.io/rss/all", "fetch", "", 100, transport = client, router = DirectPageRouter) }.isFailure)
        assertEquals(listOf("https://example.org/rss"), requests)
    }
    @Test fun redirectToProtectedSourceStopsBeforeDirectConnectionWithoutPlugin() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
                .header("Location", "https://news.zerkalo.io/story").body("".toResponseBody()).build()
        }.build()
        assertTrue(runCatching { NewsHttp.download("https://example.org/rss", "fetch", "", 100, transport = client, router = DirectPageRouter) }.isFailure)
        assertEquals(listOf("https://example.org/rss"), requests)
    }
}
