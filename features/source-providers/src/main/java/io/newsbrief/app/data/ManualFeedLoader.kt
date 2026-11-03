package io.newsbrief.app.data

import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.util.LocalizedFailure
import io.newsbrief.modules.core.api.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Source fetching remains usable before background synchronization is installed. */
class ManualFeedLoader(private val db: FeedStore, private val collector: NewsCollector,
    private val onFeedUpdated: () -> Unit = {}) : FeedLoader {
    private val mutex = Mutex()
    override suspend fun refresh(): RefreshResult = mutex.withLock { load() }
    private suspend fun load(): RefreshResult {
        val sources = db.sourceDao().enabled().filter(collector::supports).sortedByDescending { it.id }
        var total = 0
        val errors = mutableListOf<String>()
        val failures = mutableListOf<SourceFailure>()
        for (source in sources) {
            try {
                val batch = collector.collectSince(source)
                val items = batch.articles.filter {
                    // Reject broken/future timestamps (5 min of clock skew allowed).
                    it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
                }
                // Commit the checkpoint only after every fetched article has been persisted.
                val persist: suspend () -> Int = {
                    // Cross-feed duplicate links are ignored by unique SQLite index.
                    val results = db.articleDao().insertAll(items)
                    val added = results.count { it != -1L }
                    // Existing v0.3 articles had no image/full-text columns. Backfill
                    // metadata from refreshed feeds without overwriting saved reader text.
                    items.zip(results).forEach { (item, id) ->
                        if (id == -1L) {
                            db.articleDao().fillSourcePlugin(item.link, item.sourcePluginId)
                            db.articleDao().associateEnabledSource(item.link, item.sourceId, item.sourceName, item.sourcePluginId)
                            mergePluginPost(item)
                            if (item.content.isNotBlank() || item.imageUrl.isNotBlank() || item.linksJson != "[]") {
                                db.articleDao().fillMissingPreview(item.link, item.content, item.imageUrl, item.linksJson)
                            }
                        }
                    }
                    if (batch.historyGap) {
                        db.sourceDao().updateFetchProgress(source.id, null, source.fetchCursor, true)
                    } else if (items.size == batch.articles.size && batch.nextCursor != null) {
                        db.sourceDao().updateFetchProgress(source.id, batch.nextCursor, source.fetchCursor, false)
                    }
                    added
                }
                total += if (db is NewsStore) db.transaction { persist() } else persist()
                if (batch.historyGap) {
                    val failure = LocalizedFailure(R.string.nb_source_history_gap, "Some older posts could not be recovered from this source.")
                    errors += "${source.name}: ${failure.message}"
                    failures += SourceFailure(source.name, failure)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                errors += "${source.name}: ${e.message.orEmpty().take(120)}"
                failures += SourceFailure(source.name, e)
            }
        }
        // Do not silently destroy history. A future retention policy can be user-controlled.
        onFeedUpdated()
        return RefreshResult(added = total, sources = sources.size, errors = errors, sourceFailures = failures)
    }

    private suspend fun mergePluginPost(incoming: ArticleEntity) {
        val existing = db.articleDao().findByLink(incoming.link) ?: return
        if (existing.sourceId != incoming.sourceId) return
        val merged = collector.merge(existing, incoming).copy(readAt = existing.readAt)
        if (merged != existing) db.articleDao().updateArticle(merged)
    }

}
