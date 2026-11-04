package io.newsbrief.app.features

import io.newsbrief.app.data.*
import io.newsbrief.app.network.SourcePluginRegistry
import io.newsbrief.app.runtime.*

/** Registry and minimal defaults only; no management, catalog or provider implementation. */
class SourceProvidersFeature : AppFeature {
    override val id = "source-providers"
    override fun configure(bindings: FeatureBindings) {
        bindings.feedLoader = { ManualFeedLoader(it.store, it.collector, it.onFeedUpdated) }
        bindings.sourceActions = { SourceActionsService(it) }
        bindings.sources = { settings -> SourcePluginRegistry(bindings.sourcePlugins.map { it(settings) }) }

    }
}
