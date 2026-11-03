package io.newsbrief.app.data
import io.newsbrief.app.runtime.*
class SourceActionsService(private val services: FeatureServices) : SourceActions {
    private val db = services.store
    private val collector = services.collector
    private val sourcePlugins = services.sources
    private val onFeedUpdated = services.onFeedUpdated
    override suspend fun add(name: String, kind: String, address: String): Boolean {
        require(name.isNotBlank()) { "Укажи название источника" }
        val address = sourcePlugins.canonicalAddress(address, kind)
        return db.sourceDao().insert(SourceEntity(name = name.trim().take(70), kind = kind, address = address)) != -1L
    }

    override suspend fun connect(source: io.newsbrief.app.network.DirectorySource): Boolean {
        val address = sourcePlugins.canonicalAddress(source.address, source.kind)
        val candidate = SourceEntity(name = source.title, address = address, kind = source.kind,
            categoriesJson = org.json.JSONArray(source.categories.map { it.name }).toString())
        val items = collector.collect(candidate).filter {
            it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
        }
        require(items.isNotEmpty()) { "Источник не отдаёт доступных публикаций. Попробуй другой результат." }
        val added = db.transaction {
            val id = db.sourceDao().insert(candidate)
            if (id == -1L) return@transaction false
            val articles = items.map { it.copy(sourceId = id, sourceName = candidate.name) }
            val inserted = db.articleDao().insertAll(articles)
            articles.zip(inserted).forEach { (article, insertedId) ->
                if (insertedId == -1L) {
                    db.articleDao().fillSourcePlugin(article.link, article.sourcePluginId)
                    db.articleDao().associateEnabledSource(article.link, id, candidate.name, article.sourcePluginId)
                    mergePluginPost(article)
                    db.articleDao().fillMissingPreview(article.link, article.content, article.imageUrl, article.linksJson)
                }
            }
            true
        }
        if (added) onFeedUpdated()
        return added
    }

    private suspend fun mergePluginPost(incoming: ArticleEntity) {
        val existing = db.articleDao().findByLink(incoming.link) ?: return
        if (existing.sourceId != incoming.sourceId) return
        val merged = sourcePlugins.merge(existing, incoming)
        if (merged != existing) db.articleDao().updateArticle(merged)
    }

}
