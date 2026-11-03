package io.newsbrief.app.runtime

import android.content.Context
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*

interface FeatureRuntimeOwner { val runtime: FeatureRuntime }

class FeatureRuntime(private val context: Context, val features: List<AppFeature>, collector: NewsCollector? = null) {
    val bindings = FeatureBindings.create(features)
    private val persistent = bindings.persistentStorage?.invoke(context)
    val store: NewsStore = persistent?.first ?: MemoryNewsStore()
    val settings: AppSettings = persistent?.second ?: MemorySettings()
    private val sourceRegistry = bindings.sources?.invoke(settings) ?: EmptySourceRegistry()
    val services = FeatureServices(store, settings, collector = collector, registry = sourceRegistry,
        bindings = bindings, onFeedUpdated = { bindings.feedUpdated(context) })
    val backgroundTasks = object : io.newsbrief.app.sync.BackgroundTasks {
        override suspend fun refresh() = services.refresh()
        override suspend fun prefetch() = prefetchArticles?.invoke() ?: io.newsbrief.app.sync.PrefetchOutcome(0, 0, 0)
    }
    private val prefetchArticles = services.articles?.let { bindings.prefetch?.invoke(it, settings, context) }
    private var started = false
    /** Called after the Application has published this fully constructed runtime. */
    @Synchronized fun start() {
        if (started) return
        started = true
        try { bindings.start(context) }
        catch (error: Throwable) { started = false; throw error }
    }
}
