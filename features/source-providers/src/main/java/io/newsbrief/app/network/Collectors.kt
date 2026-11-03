package io.newsbrief.app.network

import io.newsbrief.app.data.*

/** Compatibility transport entry point with an explicitly supplied provider registry. */
class WebCollectors(override val plugins: SourcePluginRegistry) : NewsCollector, SourceRegistryOwner {
    override fun supports(source: SourceEntity) = plugins.supports(source)
    override suspend fun collect(source: SourceEntity) = plugins.collect(source)
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity) = plugins.merge(existing, incoming)
}
