package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FeedSyncCooldownTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun settings() = SettingsStore(app).apply {
        markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded(); markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
    }
    @Test fun attemptsAreThrottledForThreeMinutesAcrossRepositoryRecreationIncludingFailures() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            db.sourceDao().insert(SourceEntity(name = "News", kind = SourceKind.RSS, address = "https://news.example/rss"))
            var now = 1_000_000L; var calls = 0; var scheduled = 0
            val collector = object : NewsCollector {
                override suspend fun collect(source: SourceEntity): List<ArticleEntity> { calls++; error("Offline") }
            }
            val first = TestNewsRepository(db, collector, settings(), clock = { now }, onFeedUpdated = { scheduled++ })
            assertEquals(1, first.refresh().errors.size)
            now += 179_999
            val recreated = TestNewsRepository(db, collector, settings(), clock = { now }, onFeedUpdated = { scheduled++ })
            val skipped = recreated.refresh()
            assertTrue(skipped.skipped); assertEquals(1L, skipped.retryAfterMillis)
            assertEquals(1, calls); assertEquals(1, scheduled)
            now++
            assertFalse(recreated.refresh().skipped)
            assertEquals(2, calls)
            // A backwards clock correction must not suppress updates indefinitely.
            now = 5
            assertFalse(recreated.refresh().skipped); assertEquals(3, calls)
        } finally { db.close() }
    }
    @Test fun concurrentRequestsShareOneAttemptAndSourceChangesCanLoadImmediately() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            db.sourceDao().insert(SourceEntity(name = "First", kind = SourceKind.RSS, address = "https://first.example/rss"))
            val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var calls = 0
            val repo = TestNewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                    calls++; if (calls == 1) { started.complete(Unit); release.await() }; return emptyList()
                }
            }, settings(), clock = { 1_000_000L })
            val first = async { repo.refresh() }; started.await()
            val duplicate = async { repo.refresh() }; release.complete(Unit)
            assertFalse(first.await().skipped); assertTrue(duplicate.await().skipped); assertEquals(1, calls)
            repo.addSource("Second", SourceKind.RSS, "https://second.example/rss")
            assertFalse(repo.refresh().skipped); assertEquals(3, calls)
            assertTrue(repo.refresh().skipped); assertEquals(3, calls)
        } finally { db.close() }
    }
}
