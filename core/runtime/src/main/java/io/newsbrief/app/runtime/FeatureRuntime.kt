package io.newsbrief.app.runtime

import android.content.Context
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*

interface FeatureRuntimeOwner { val runtime: FeatureRuntime }

class FeatureRuntime(context: Context, val features: List<AppFeature> = InstalledFeatures.create()) {
    val bindings = FeatureBindings.create(features)
    private val persistent = bindings.persistentStorage?.invoke(context)
    val store: NewsStore = persistent?.first ?: MemoryNewsStore()
    val settings: AppSettings = persistent?.second ?: MemorySettings()
    private val sourceRegistry = bindings.sources?.invoke(settings) ?: EmptySourceRegistry()
    val repository = NewsRepository(store, sourceRegistry, settings, sourcePlugins = sourceRegistry,
        extensions = bindings, onFeedUpdated = { bindings.feedUpdated(context) })
    val backgroundTasks = object : io.newsbrief.app.sync.BackgroundTasks {
        override suspend fun refresh() = repository.refresh()
        override suspend fun prefetch() = prefetchArticles?.invoke() ?: io.newsbrief.app.sync.PrefetchOutcome(0, 0, 0)
    }
    private val prefetchArticles = if (bindings.article != null) bindings.prefetch?.invoke(repository, settings, context) else null
    init { bindings.start(context) }
}
