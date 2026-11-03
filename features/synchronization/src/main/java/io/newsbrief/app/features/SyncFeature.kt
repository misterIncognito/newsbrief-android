package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.sync.*
import io.newsbrief.app.network.ArticlePrefetcher
import io.newsbrief.app.worker.*

class SyncFeature : AppFeature {
    override val id = "synchronization"
    override val order = 30
    override fun configure(bindings: FeatureBindings) {
        bindings.refresh = { db, collector, settings, clock, loader ->
            val sync = FeedSynchronizer(db, collector, settings, loader, clock)
            sync::refresh
        }
        bindings.prefetch = { repo, settings, context ->
            val images = bindings.images?.invoke(context)
            val prefetch = images?.let { store ->
                ArticlePrefetcher(repo, settings, store) { url, worker ->
                    bindings.pageProxyPlugins.firstNotNullOfOrNull { it.originalResource(url, worker) } ?: url
                }
            }
            val run: suspend () -> PrefetchOutcome = { prefetch?.prefetch()?.let { PrefetchOutcome(it.articles, it.images, it.failures) } ?: PrefetchOutcome(0, 0, 0) }
            run
        }
        bindings.start = { RefreshWorker.schedule(it) }
        bindings.feedUpdated = { PrefetchWorker.enqueue(it) }
    }
}
