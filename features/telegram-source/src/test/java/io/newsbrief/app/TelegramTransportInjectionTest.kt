package io.newsbrief.app

import io.newsbrief.app.data.MemorySettings
import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.features.TelegramSourceFeature
import io.newsbrief.app.network.DownloadedResource
import io.newsbrief.app.network.HttpTransport
import io.newsbrief.app.network.PageRequest
import io.newsbrief.app.runtime.FeatureBindings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramTransportInjectionTest {
    @Test fun usesTransportFromItsComposition() = runBlocking {
        val bindings = FeatureBindings.create(listOf(TelegramSourceFeature()))
        var requested = ""
        bindings.http = object : HttpTransport {
            override fun resource(request: PageRequest, configuration: String, maxBytes: Int): DownloadedResource {
                requested = request.resource
                val html = """<div class="tgme_widget_message_wrap"><div class="tgme_widget_message" data-post="example/1"><div class="tgme_widget_message_text">Injected post</div></div></div>"""
                return DownloadedResource(html.toByteArray(), "text/html", "UTF-8", request.resource)
            }
        }
        val plugin = bindings.sourcePlugins.single()(MemorySettings())
        val source = SourceEntity(name = "Example", kind = SourceKind.TELEGRAM, address = "https://t.me/s/example")
        val articles = plugin.collect(source)
        assertEquals(source.address, requested)
        assertEquals(1, articles.size)
    }
}
