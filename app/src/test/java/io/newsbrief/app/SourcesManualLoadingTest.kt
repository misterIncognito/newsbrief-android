package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.features.SourceProvidersFeature
import io.newsbrief.app.features.RssSourceFeature
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.FeatureBindings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SourcesManualLoadingTest {
    @Test fun atomLoadsIntoSessionStoreWithoutSyncReaderAiOrRoom() = runBlocking {
        val db = MemoryNewsStore()
        val settings = MemorySettings().apply { markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded(); markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded() }
        val bindings = FeatureBindings.create(listOf(SourceProvidersFeature(), RssSourceFeature()))
        assertNull(bindings.refresh); assertNull(bindings.article); assertNull(bindings.persistentStorage); assertNull(bindings.generate)
        val collector = SourcePluginRegistry(listOf(RssSourcePlugin { """<feed xmlns="http://www.w3.org/2005/Atom"><entry>
            <title>Atom story</title><link href="https://example.org/story"/>
            <content type="html">&lt;p&gt;Offline feed content&lt;/p&gt;</content>
            <updated>2026-01-01T12:00:00Z</updated></entry></feed>""" }))
        val repo = NewsRepository(db, collector, settings, extensions = bindings)
        assertTrue(repo.addSource("Atom", SourceKind.RSS, "https://example.org/feed"))
        assertEquals(1, repo.refresh().added)
        val item = repo.observeLatest().first().single()
        assertEquals("Atom story", item.title)
        assertEquals("Offline feed content", repo.readArticle(item).text)
        assertEquals(0, repo.refresh().added)
        assertEquals(1, repo.observeLatest().first().size)
    }
}
