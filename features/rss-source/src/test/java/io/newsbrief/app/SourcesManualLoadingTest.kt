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
        val repo = io.newsbrief.app.runtime.FeatureServices(db, settings, collector, bindings = bindings)
        assertTrue(repo.sourceActions!!.add("Atom", SourceKind.RSS, "https://example.org/feed"))
        val legacyId = db.sourceDao().insert(SourceEntity(name = "Offline Telegram", kind = SourceKind.TELEGRAM_PROXY, address = "telegram-proxy:legacy"))
        val cached = ArticleEntity(sourceId = legacyId, sourceName = "Offline Telegram", title = "Cached post", excerpt = "Saved text", content = "Offline body", link = "https://t.me/legacy/1", publishedAt = 1)
        db.articleDao().insertAll(listOf(cached))
        val outcome = repo.refresh()
        assertEquals(1, outcome.added)
        assertEquals(1, outcome.sources)
        assertTrue(outcome.errors.isEmpty())
        assertEquals("Offline body", db.articleDao().findByLink(cached.link)!!.content)
        val item = db.articleDao().observeRecent(0).first().single { it.link == "https://example.org/story" }
        assertEquals("Atom story", item.title)
        assertEquals("Offline feed content", item.content)
        assertEquals(0, repo.refresh().added)
        assertEquals(2, db.articleDao().observeRecent(0).first().size)
    }
}
