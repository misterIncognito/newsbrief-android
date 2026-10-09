package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.sync.*
import io.newsbrief.app.network.ArticlePrefetcher
import io.newsbrief.app.worker.*

class SyncFeature : AppFeature {
    override val id = "synchronization"
    override val order = 30
    override fun configure(bindings: FeatureBindings) {
        bindings.refresh = { db, collector, settings, clock, updated ->
            val sync = FeedSynchronizer(db, collector, settings, clock, updated)
            sync::refresh
        }
        bindings.prefetch = { repo, settings, context ->
            val images = bindings.images?.invoke(context)
            val prefetch = images?.let { ArticlePrefetcher(repo, settings, it) }
            val run: suspend () -> PrefetchOutcome = { prefetch?.prefetch()?.let { PrefetchOutcome(it.articles, it.images, it.failures) } ?: PrefetchOutcome(0, 0, 0) }
            run
        }
        bindings.start = { RefreshWorker.schedule(it) }
        bindings.feedUpdated = { PrefetchWorker.enqueue(it) }
    }
}
