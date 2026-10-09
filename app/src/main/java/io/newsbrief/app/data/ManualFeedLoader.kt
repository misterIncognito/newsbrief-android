package io.newsbrief.app.data

import io.newsbrief.app.network.NewsCollector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Source fetching remains usable before background synchronization is installed. */
class ManualFeedLoader(private val db: FeedStore, private val collector: NewsCollector,
    private val onFeedUpdated: () -> Unit = {}) {
    private val mutex = Mutex()
    suspend fun refresh(): RefreshResult = mutex.withLock { load() }
    private suspend fun load(): RefreshResult {
        val sources = db.sourceDao().enabled().sortedByDescending { it.id }
        var total = 0
        val errors = mutableListOf<String>()
        for (source in sources) {
            try {
                val items = collector.collect(source).filter {
                    // Reject broken/future timestamps (5 min of clock skew allowed).
                    it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
                }
                // Cross-feed duplicate links are ignored by unique SQLite index.
                val results = db.articleDao().insertAll(items)
                total += results.count { it != -1L }
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
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                errors += "${source.name}: ${e.message.orEmpty().take(120)}"
            }
        }
        // Do not silently destroy history. A future retention policy can be user-controlled.
        onFeedUpdated()
        return RefreshResult(added = total, sources = sources.size, errors = errors)
    }

    private suspend fun mergePluginPost(incoming: ArticleEntity) {
        val existing = db.articleDao().findByLink(incoming.link) ?: return
        if (existing.sourceId != incoming.sourceId) return
        val merged = collector.merge(existing, incoming)
        if (merged != existing) db.articleDao().updateArticle(merged)
    }

}
