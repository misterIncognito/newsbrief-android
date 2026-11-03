package io.newsbrief.app.network

import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.LocalizedFailure
import io.newsbrief.app.util.requireLocalized
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourcePluginRegistry(plugins: List<NewsSourcePlugin>) : SourceRegistry {
    private val byId = plugins.associateBy { it.info.id }
    override val available = plugins.map { it.info }
    init { require(byId.size == plugins.size) { "Duplicate source plugin ID" } }
    override fun plugin(id: String): NewsSourcePlugin = byId[id] ?: throw LocalizedFailure(R.string.nb_err_plugin_unavailable, "Плагин источника недоступен: $id", id)
    override fun canonicalAddress(input: String, id: String) = plugin(id).canonicalAddress(input)
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity =
        byId[incoming.sourcePluginId]?.merge(existing, incoming) ?: existing
    override suspend fun collect(source: SourceEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        plugin(source.kind).collect(source).map { it.copy(sourcePluginId = source.kind) }
    }
    override suspend fun collectSince(source: SourceEntity): SourceBatch = withContext(Dispatchers.IO) {
        val batch = plugin(source.kind).collectSince(source)
        batch.copy(articles = batch.articles.map { it.copy(sourcePluginId = source.kind) })
    }
}
