package io.newsbrief.app.network

import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourcePluginRegistry(plugins: List<NewsSourcePlugin>) : SourceRegistry {
    private val byId = plugins.associateBy { it.info.id }
    override val available = plugins.map { it.info }
    init { require(byId.size == plugins.size) { "Duplicate source plugin ID" } }
    override fun plugin(id: String): NewsSourcePlugin = byId[id] ?: error("Плагин источника недоступен: $id")
    override fun canonicalAddress(input: String, id: String) = plugin(id).canonicalAddress(input)
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity =
        byId[incoming.sourcePluginId]?.merge(existing, incoming) ?: existing
    override suspend fun collect(source: SourceEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        plugin(source.kind).collect(source).map { it.copy(sourcePluginId = source.kind) }
    }
    companion object {
        fun builtIns(settings: SourceSettings? = null, overrides: List<NewsSourcePlugin> = emptyList()): SourcePluginRegistry {
            val config = settings ?: MemorySettings()
            val replacements = overrides.map { it.info.id }.toSet()
            val plugins = io.newsbrief.app.runtime.FeatureBindings.create().sourcePlugins.map { it(config) }
            return SourcePluginRegistry(plugins.filterNot { it.info.id in replacements } + overrides)
        }
    }
}
