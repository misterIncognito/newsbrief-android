package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.data.MemorySettings
import io.newsbrief.app.features.RssSourceFeature
import io.newsbrief.app.network.DownloadedResource
import io.newsbrief.app.network.HttpTransport
import io.newsbrief.app.network.PageRequest
import io.newsbrief.app.runtime.FeatureBindings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RssTransportInjectionTest {
    @Test fun usesTransportFromItsComposition() = runBlocking {
        val bindings = FeatureBindings.create(listOf(RssSourceFeature()))
        var requested = ""
        bindings.http = object : HttpTransport {
            override fun resource(request: PageRequest, configuration: String, maxBytes: Int): DownloadedResource {
                requested = request.resource
                val xml = "<rss><channel><item><title>Injected</title><link>https://example.org/story</link></item></channel></rss>"
                return DownloadedResource(xml.toByteArray(), "application/rss+xml", "UTF-8", request.resource)
            }
        }
        val plugin = bindings.sourcePlugins.single()(MemorySettings())
        val source = SourceEntity(name = "Example", kind = SourceKind.RSS, address = "https://example.org/feed")
        val articles = plugin.collect(source)
        assertEquals(source.address, requested)
        assertEquals("Injected", articles.single().title)
    }
}
