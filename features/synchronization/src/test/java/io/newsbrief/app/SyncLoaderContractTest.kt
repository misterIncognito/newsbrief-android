package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.sync.FeedSynchronizer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Scheduling is tested against its loader contract without any source implementation. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class SyncLoaderContractTest {
    @Test fun prefetchUsesProxyFromTheProvidedComposition() = runBlocking {
        val article = ArticleEntity(sourceId = 1, sourceName = "source", title = "title", excerpt = "",
            link = "https://example.org/story", publishedAt = 1, readerLoaded = true,
            content = "body", imageUrl = "https://proxy.example/image")
        val repository = object : PrefetchArticleStore {
            override suspend fun lookup(url: String): ArticleEntity? = null
            override suspend fun prefetchCandidates() = listOf(article)
            override suspend fun linkedArticle(parent: ArticleEntity, url: String) = parent
            override suspend fun readArticle(article: ArticleEntity, refresh: Boolean, preserveLongerText: Boolean) = error("unexpected read")
        }
        var loaded = ""
        val images = object : PrefetchImageStore {
            override fun contains(url: String) = false
            override suspend fun load(url: String, worker: String): ByteArray { loaded = url; return byteArrayOf() }
        }
        val bindings = io.newsbrief.app.runtime.FeatureBindings.create(listOf(io.newsbrief.app.features.SyncFeature()))
        bindings.images = { images }
        bindings.pageProxyPlugins += object : PageProxyPlugin {
            override val id = "test"
            override fun supports(request: PageRequest) = true
            override fun endpoint(request: PageRequest, configuration: String) = request.resource
            override fun originalResource(url: String, configuration: String) = "https://origin.example/image"
        }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val outcome = bindings.prefetch!!.invoke(repository, MemorySettings(), context).invoke()
        assertEquals("https://origin.example/image", loaded)
        assertEquals(1, outcome.images)
    }
    private val collector = object : NewsCollector {
        override suspend fun collect(source: SourceEntity): List<ArticleEntity> = error("Must delegate to loader")
    }
    @Test fun delegatesOnceAndHonorsCooldownWithoutAnyProvider() = runBlocking {
        var calls = 0; var now = 1L
        val loader = FeedLoader { calls++; RefreshResult(4, 0, emptyList()) }
        val sync = FeedSynchronizer(MemoryNewsStore(), collector, TestSettings(), loader, { now })
        assertEquals(4, sync.refresh().added)
        assertTrue(sync.refresh().skipped); assertEquals(1, calls)
        now += FeedSynchronizer.SYNC_COOLDOWN_MILLIS
        assertEquals(4, sync.refresh().added); assertEquals(2, calls)
    }
    @Test fun cancellationFromLoaderRemainsCancellation() = runBlocking {
        val sync = FeedSynchronizer(MemoryNewsStore(), collector, TestSettings(),
            FeedLoader { throw CancellationException("closed") })
        try { sync.refresh(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
}
