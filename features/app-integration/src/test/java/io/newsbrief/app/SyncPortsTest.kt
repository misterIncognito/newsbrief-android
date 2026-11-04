package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.sync.FeedSynchronizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** Runs without Room, HTTP, sources, reader, UI or an Application. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class SyncPortsTest {
    private class Memory : NewsStore {
        val sources = mutableListOf(SourceEntity(id = 1, name = "Test", address = "https://example.org/rss", kind = SourceKind.RSS))
        val articles = linkedMapOf<String, ArticleEntity>()
        private val sourcePort = object : SourceDao {
            override fun observe() = flowOf(sources.toList())
            override suspend fun enabled() = sources.filter { it.enabled }
            override suspend fun count() = sources.size
            override suspend fun insert(source: SourceEntity): Long = error("unused")
            override suspend fun setEnabled(id: Long, enabled: Boolean) = error("unused")
            override suspend fun delete(id: Long) = error("unused")
        }
        private val articlePort = object : ArticleDao {
            override fun observeAll(): kotlinx.coroutines.flow.Flow<List<ArticleEntity>> = error("unused")
            override fun observeSaved() = flowOf(emptyList<ArticleEntity>())
            override fun observeRecent(from: Long) = flowOf(articles.values.toList())
            override fun observeFiltered(sourceIds: List<Long>, filtered: Boolean, limit: Int) = observeRecent(0)
            override suspend fun recent(from: Long, limit: Int) = articles.values.toList()
            override suspend fun insertAll(items: List<ArticleEntity>) = items.map {
                if (articles.containsKey(it.link)) -1L else { articles[it.link] = it; articles.size.toLong() }
            }
            override suspend fun updateArticle(article: ArticleEntity) { articles[article.link] = article }
            override suspend fun findByLink(url: String) = articles[url]
            override suspend fun fillSourcePlugin(url: String, pluginId: String) = Unit
            override suspend fun requireProxy(url: String) = Unit
            override suspend fun updateReader(url: String, title: String, content: String, imageUrl: String,
                publishedAt: Long, author: String, imagesJson: String, linksJson: String) = error("unused")
            override suspend fun fillMissingPreview(url: String, content: String, imageUrl: String, linksJson: String) = Unit
            override suspend fun associateEnabledSource(url: String, sourceId: Long, sourceName: String, sourcePluginId: String) = Unit
            override suspend fun deleteOld(cutoff: Long) = error("unused")
        }
        override fun sourceDao() = sourcePort
        override fun articleDao() = articlePort
        override fun digestDao(): DigestDao = error("unused")
        override fun catalogSearchDao(): CatalogSearchDao = error("unused")
        override fun savedArticleDao(): SavedArticleDao = error("unused")
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }
    private fun article(source: SourceEntity) = ArticleEntity(sourceId = source.id, sourceName = source.name,
        title = "Story", excerpt = "", link = "https://example.org/story", publishedAt = 1)

    private fun synchronizer(db: FeedStore, collector: NewsCollector, settings: SyncSettings,
        clock: () -> Long = System::currentTimeMillis) =
        FeedSynchronizer(db, collector, settings, ManualFeedLoader(db, collector), clock)

    @Test fun concurrentAttemptsAreSerializedAndCooldownExpires() = runBlocking {
        val memory = Memory(); val settings = TestSettings(); var time = 1_000L; var calls = 0
        val sync = synchronizer(memory, object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                calls++; delay(10); return listOf(article(source))
            }
        }, settings, clock = { time })
        val results = listOf(async { sync.refresh() }, async { sync.refresh() }).awaitAll()
        assertEquals(1, calls); assertEquals(1, results.count { it.skipped }); assertEquals(1, memory.articles.size)
        time += FeedSynchronizer.SYNC_COOLDOWN_MILLIS
        assertFalse(sync.refresh().skipped); assertEquals(2, calls)
    }
    @Test fun sourceConfigurationChangeBypassesCooldown() = runBlocking {
        val memory = Memory(); val settings = TestSettings()
        val sync = synchronizer(memory, object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = listOf(article(source))
        }, settings)
        sync.refresh()
        memory.sources += memory.sources.single().copy(id = 2, address = "https://example.org/new")
        assertFalse(sync.refresh().skipped)
        settings.setWorkerUrl("https://worker.example")
        assertFalse(sync.refresh().skipped)
    }
    @Test fun failuresRecordAttemptsAndNeverCallReaderOrStorageImplementation() = runBlocking {
        var calls = 0
        val sync = synchronizer(Memory(), object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> { calls++; error("offline") }
        }, TestSettings())
        assertEquals(1, sync.refresh().errors.size)
        assertTrue(sync.refresh().skipped); assertEquals(1, calls)
    }
    @Test fun cancellationPropagates() = runBlocking {
        val sync = synchronizer(Memory(), object : NewsCollector {
            override suspend fun collect(source: SourceEntity): List<ArticleEntity> = throw CancellationException("stopped")
        }, TestSettings())
        try { sync.refresh(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun duplicateContentUsesCollectorMergePort() = runBlocking {
        val memory = Memory(); var time = 1L
        val sync = synchronizer(memory, object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = listOf(article(source))
            override fun merge(existing: ArticleEntity, incoming: ArticleEntity) = existing.copy(content = "Merged post")
        }, TestSettings(), clock = { time })
        sync.refresh(); time += FeedSynchronizer.SYNC_COOLDOWN_MILLIS; sync.refresh()
        assertEquals("Merged post", memory.articles.values.single().content)
    }
    @Test fun prefetchWorksThroughArticleAndImagePorts() = runBlocking {
        val post = article(Memory().sources.single()).copy(link = "https://t.me/channel/1", sourcePluginId = SourceKind.TELEGRAM,
            linksJson = ArticleLinks.encode(listOf(ArticleLink("Story", "https://example.org/full"))))
        var fetched: ArticleEntity? = null
        val articles = object : PrefetchArticleStore {
            override suspend fun lookup(url: String): ArticleEntity? = null
            override suspend fun prefetchCandidates() = listOf(post)
            override suspend fun linkedArticle(parent: ArticleEntity, url: String) = parent.copy(link = url,
                sourcePluginId = "", proxyRequired = true)
            override suspend fun readArticle(article: ArticleEntity, refresh: Boolean, preserveLongerText: Boolean): ReaderArticle {
                fetched = article
                return ReaderArticle("Story", "Full text", "https://example.org/image.png", article.link)
            }
        }
        var images = 0
        val cache = object : PrefetchImageStore {
            override fun contains(url: String) = false
            override suspend fun load(url: String, worker: String): ByteArray { images++; return byteArrayOf(1) }
        }
        val result = ArticlePrefetcher(articles, TestSettings(), cache).prefetch()
        assertEquals("https://example.org/full", fetched!!.link); assertTrue(fetched.proxyRequired)
        assertEquals(1, result.articles); assertEquals(1, images)
    }
}
