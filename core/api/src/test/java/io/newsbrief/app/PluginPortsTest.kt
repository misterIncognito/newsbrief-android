package io.newsbrief.app

import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import org.junit.Assert.*
import org.junit.Test

class FixtureModulePlugin : AppFeature {
    override val id = "fixture-module"
    override fun configure(bindings: FeatureBindings) {
        bindings.http = object : HttpTransport {
            override fun resource(request: PageRequest, configuration: String, maxBytes: Int) =
                DownloadedResource(request.resource.toByteArray(), "text/plain", "UTF-8", request.resource)
        }
        bindings.parseArticle = { raw, url -> ReaderArticle("Fixture", raw, "", url) }
    }
}
class PluginPortsTest {
    @Test fun conflictingProvidersFailInsteadOfReplacingOneAnother() {
        val first = FixtureModulePlugin()
        val second = FixtureModulePlugin()
        val error = assertThrows(IllegalStateException::class.java) {
            FeatureBindings.create(listOf(first, second))
        }
        assertEquals("Duplicate provider: http", error.message)
    }
    @Test fun independentlyRegisteredLibraryConnectsThroughSharedInterfaces() {
        assertEquals(listOf("fixture-module"), InstalledFeatures.create().map { it.id })
        val bindings = FeatureBindings.create(listOf(FixtureModulePlugin()))
        val url = "https://example.org/article"
        assertEquals(url, bindings.http!!.resource(PageRequest(url, "fetch", false), "", 1024).bytes.toString(Charsets.UTF_8))
        assertEquals("Article body", bindings.parseArticle!!("Article body", url).text)
    }
    @Test fun emptyCompositionExposesNoTransportParserOrOptionalActions() {
        val bindings = FeatureBindings.create(emptyList())
        assertNull(bindings.http)
        assertNull(bindings.parseArticle)
        assertNull(bindings.saveArticle)
        assertTrue(bindings.articleActions.isEmpty())
        assertTrue(bindings.sourcePlugins.isEmpty())
    }
}
