package io.newsbrief.app

import io.newsbrief.app.network.*
import org.junit.Assert.*
import org.junit.Test

class PageProxyRegistryTest {
    @Test fun missingProxyRefusesProtectedAndExplicitRequests() {
        for (router in listOf(DirectPageRouter, PageProxyRegistry(emptyList()))) {
            assertEquals("https://example.org/rss", router.route(PageRequest("https://example.org/rss", "fetch", false), ""))
            assertTrue(runCatching { router.route(PageRequest("https://meduza.io/story", "fetch", false), "") }.isFailure)
            assertTrue(runCatching { router.route(PageRequest("https://example.org/story", "fetch", true), "") }.isFailure)
        }
    }
    @Test fun ordinaryImagesCanBeDirectWhenNoPluginIsInstalledButProtectedImagesCannot() {
        val router = PageProxyRegistry(emptyList())
        val normal = PageRequest("https://cdn.example/image.png", "image", false, preferProxy = true)
        assertEquals(normal.resource, router.route(normal, ""))
        assertTrue(runCatching { router.route(normal.copy(proxyRequired = true), "") }.isFailure)
    }
    @Test fun pluginsAreIndependentOfWorkerAndFailureNeverSelectsAnotherRoute() {
        var second = 0
        fun plugin(id: String, fail: Boolean = false) = object : PageProxyPlugin {
            override val id = id
            override fun supports(request: PageRequest) = true
            override fun endpoint(request: PageRequest, configuration: String): String {
                if (fail) error("Service unavailable")
                second++; return "https://another-proxy.example/resource"
            }
        }
        val request = PageRequest("https://example.org/story", "fetch", true)
        assertEquals("https://another-proxy.example/resource", PageProxyRegistry(listOf(plugin("custom"))).route(request, ""))
        second = 0
        assertTrue(runCatching { PageProxyRegistry(listOf(plugin("failed", true), plugin("other"))).route(request, "") }.isFailure)
        assertEquals(0, second)
        assertTrue(runCatching { PageProxyRegistry(listOf(plugin("duplicate"), plugin("duplicate"))) }.isFailure)
    }
}
