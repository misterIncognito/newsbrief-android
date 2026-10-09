package io.newsbrief.app.network

import io.newsbrief.app.data.*

/** Compatibility transport entry point; only installed providers are registered. */
class WebCollectors(settings: SourceSettings? = null,
    override val plugins: SourcePluginRegistry = SourcePluginRegistry.builtIns(settings)) : NewsCollector, SourceRegistryOwner {
    override fun supports(source: SourceEntity) = plugins.supports(source)
    override suspend fun collect(source: SourceEntity) = plugins.collect(source)
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity) = plugins.merge(existing, incoming)
}
