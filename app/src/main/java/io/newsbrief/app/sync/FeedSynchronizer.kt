package io.newsbrief.app.sync

import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeedSynchronizer(private val db: FeedStore, private val collector: NewsCollector,
    private val settings: SyncSettings, private val clock: () -> Long = System::currentTimeMillis,
    private val onFeedUpdated: () -> Unit = {}) {
    private val refreshMutex = Mutex()
    private val loader = ManualFeedLoader(db, collector, onFeedUpdated)
    companion object { const val SYNC_COOLDOWN_MILLIS = 3 * 60_000L }
    suspend fun refresh(): RefreshResult = refreshMutex.withLock {
        // Fetch newly added sources first so slow existing feeds do not delay them.
        val sources = db.sourceDao().enabled().sortedByDescending { it.id }
        val configuration = org.json.JSONArray().apply {
            put(settings.workerUrl()); put(settings.telegramProxyUrl())
            sources.forEach { put(org.json.JSONArray(listOf(it.id, it.kind, it.address))) }
        }.toString()
        val now = clock()
        val previous = settings.feedSyncAttempt()
        val elapsed = previous?.let { now - it.time }
        if (previous?.configuration == configuration && elapsed != null && elapsed in 0 until SYNC_COOLDOWN_MILLIS)
            return@withLock RefreshResult(0, sources.size, emptyList(), skipped = true,
                retryAfterMillis = SYNC_COOLDOWN_MILLIS - elapsed)
        // Record attempts, not successes, before any requests. Source-list changes remain immediate.
        settings.recordFeedSyncAttempt(now, configuration)
        loader.refresh()
    }
}
