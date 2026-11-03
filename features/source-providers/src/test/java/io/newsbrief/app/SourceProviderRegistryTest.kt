package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.features.SourceProvidersFeature
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.FeatureBindings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceProviderRegistryTest {
    @Test fun defaultsWaitForProvidersAndDeletedSourcesDoNotReturn() = runBlocking {
        val settings = MemorySettings(); val store = MemoryNewsStore()
        val bindings = FeatureBindings.create(listOf(SourceProvidersFeature()))
        bindings.seed(store, settings)
        assertFalse(settings.isSourcesCatalogV5Seeded())
        bindings.sourceDefaults += SourceEntity(name = "RSS", address = "https://example.org/rss", kind = SourceKind.RSS)
        bindings.seed(store, settings)
        assertEquals(1, store.sourceDao().count())
        assertTrue(settings.isSourcesCatalogV5Seeded())
        store.sourceDao().delete(store.sourceDao().enabled().single().id)
        bindings.seed(store, settings)
        assertEquals(0, store.sourceDao().count())
    }
    @Test fun customProvidersRegisterWithoutBundledProvidersOrCatalog() = runBlocking {
        var calls = 0
        val custom = object : NewsSourcePlugin {
            override val info = SourcePluginInfo("custom", "Custom", "Address")
            override fun canonicalAddress(input: String) = "custom:" + input.trim()
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> { calls++; return emptyList() }
        }
        val bindings = FeatureBindings.create(listOf(SourceProvidersFeature()))
        assertTrue(bindings.sourcePlugins.isEmpty()); assertNull(bindings.directory); assertTrue(bindings.pageProxyPlugins.isEmpty())
        bindings.sourcePlugins += { custom }
        val registry = bindings.sources!!.invoke(MemorySettings())
        assertEquals("custom:x", registry.canonicalAddress(" x ", "custom"))
        registry.collect(SourceEntity(name = "C", kind = "custom", address = "custom:x"))
        assertTrue(runCatching { registry.collect(SourceEntity(name = "C", kind = "missing", address = "x")) }.isFailure)
        assertEquals(1, calls)
    }
}
