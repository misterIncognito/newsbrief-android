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
