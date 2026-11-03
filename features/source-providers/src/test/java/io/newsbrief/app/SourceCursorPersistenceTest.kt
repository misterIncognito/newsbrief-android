package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceCursorPersistenceTest {
    @Test fun persistsPostsAndCheckpointOnlyAfterSuccessfulCollection() = runBlocking {
        val store = MemoryNewsStore()
        val id = store.sourceDao().insert(SourceEntity(name = "Channel", address = "https://example.org", kind = "test"))
        var fail = false
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            override suspend fun collectSince(source: SourceEntity): SourceBatch {
                if (fail) error("page failed")
                return SourceBatch(listOf(ArticleEntity(sourceId = id, sourceName = source.name, title = "Item", excerpt = "",
                    link = "https://example.org/1", publishedAt = 100)), "1")
            }
        }
        val loader = ManualFeedLoader(store, collector)
        assertEquals(1, loader.refresh().added)
        assertEquals("1", store.sourceDao().observe().first().single().fetchCursor)
        assertNotNull(store.articleDao().findByLink("https://example.org/1"))
        fail = true
        assertEquals(1, loader.refresh().errors.size)
        assertEquals("1", store.sourceDao().observe().first().single().fetchCursor)
    }
    @Test fun staleRefreshCannotMoveCheckpointBackwards() = runBlocking {
        val store = MemoryNewsStore()
        val id = store.sourceDao().insert(SourceEntity(name = "Channel", address = "https://example.org", kind = "test"))
        store.sourceDao().updateFetchProgress(id, "110", "", false)
        store.sourceDao().updateFetchProgress(id, "105", "", false)
        assertEquals("110", store.sourceDao().observe().first().single().fetchCursor)
    }

    @Test fun gapsArePersistedWithoutAdvancingCheckpoint() = runBlocking {
        val store = MemoryNewsStore()
        val id = store.sourceDao().insert(SourceEntity(name = "Channel", address = "https://example.org", kind = "test", fetchCursor = "100"))
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            override suspend fun collectSince(source: SourceEntity) = SourceBatch(listOf(
                ArticleEntity(sourceId = id, sourceName = source.name, title = "New", excerpt = "",
                    link = "https://example.org/105", publishedAt = 100)), historyGap = true)
        }
        assertEquals(1, ManualFeedLoader(store, collector).refresh().added)
        val state = store.sourceDao().observe().first().single()
        assertEquals("100", state.fetchCursor)
        assertTrue(state.historyGap)
        assertNotNull(store.articleDao().findByLink("https://example.org/105"))
    }

    @Test fun connectingCatalogSourceStoresItsInitialCheckpoint() = runBlocking {
        val store = MemoryNewsStore()
        val plugin = object : NewsSourcePlugin {
            override val info = SourcePluginInfo("test", "Test", "URL")
            override fun canonicalAddress(input: String) = input
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            override suspend fun collectSince(source: SourceEntity) = SourceBatch(listOf(
                ArticleEntity(sourceId = source.id, sourceName = source.name, title = "Post", excerpt = "",
                    link = "https://example.org/1", publishedAt = 1)), "checkpoint-1")
        }
        val registry = SourcePluginRegistry(listOf(plugin))
        val services = FeatureServices(store, MemorySettings(), collector = registry, registry = registry,
            bindings = FeatureBindings.create(emptyList()))
        assertTrue(SourceActionsService(services).connect(DirectorySource("Channel", "https://example.org/feed", "test")))
        assertEquals("checkpoint-1", store.sourceDao().observe().first().single().fetchCursor)
        assertNotNull(store.articleDao().findByLink("https://example.org/1"))
    }

    @Test fun checkpointWriteFailureRollsBackArticles() = runBlocking {
        val store = MemoryNewsStore()
        val id = store.sourceDao().insert(SourceEntity(name = "S", address = "https://example.org", kind = "test"))
        val sources = object : SourceDao by store.sourceDao() {
            override suspend fun updateFetchProgress(id: Long, cursor: String?, expected: String, historyGap: Boolean) {
                error("Checkpoint write failed")
            }
        }
        val failingStore = object : NewsStore by store {
            override fun sourceDao(): SourceDao = sources
            override suspend fun <T> transaction(block: suspend () -> T): T = store.transaction(block)
        }
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            override suspend fun collectSince(source: SourceEntity) = SourceBatch(listOf(
                ArticleEntity(sourceId = id, sourceName = "S", title = "Post", excerpt = "", link = "https://example.org/new", publishedAt = 1)), "1")
        }
        val result = ManualFeedLoader(failingStore, collector).refresh()
        assertEquals(1, result.errors.size)
        assertNull(store.articleDao().findByLink("https://example.org/new"))
        assertEquals("", store.sourceDao().observe().first().single().fetchCursor)
    }

}
