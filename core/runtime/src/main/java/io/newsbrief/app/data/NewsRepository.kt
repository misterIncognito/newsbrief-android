package io.newsbrief.app.data

import io.newsbrief.app.network.ArticleLoader
import io.newsbrief.app.network.SourceRegistry
import io.newsbrief.app.network.EmptySourceRegistry
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.network.ReaderArticle
import io.newsbrief.app.network.NewsCollector
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException

class NewsRepository(
    private val db: NewsStore,
    private val collector: NewsCollector,
    private val settings: AppSettings,
    directoryNetwork: io.newsbrief.app.network.DirectoryNetwork? = null,
    sourcePlugins: SourceRegistry? = null,
    articleReader: ArticleLoader? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFeedUpdated: () -> Unit = {},
    private val extensions: FeatureBindings = FeatureBindings.create()
) : io.newsbrief.app.network.PrefetchArticleStore {
    val sourcePlugins: SourceRegistry = sourcePlugins ?: (collector as? io.newsbrief.app.network.SourceRegistryOwner)?.plugins ?: (collector as? SourceRegistry)
        ?: extensions.sources?.invoke(settings) ?: EmptySourceRegistry()
    private val articleReader = articleReader ?: extensions.article?.invoke(settings, extensions.extractor?.invoke(settings))

    companion object { const val SYNC_COOLDOWN_MILLIS = 3 * 60_000L }

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observe()
    fun observeLatest(): Flow<List<ArticleEntity>> = db.articleDao().observeRecent(0)
    fun observeSaved(): Flow<List<ArticleEntity>> = db.articleDao().observeSaved()
    fun observeSavedUrls(): Flow<List<String>> = db.savedArticleDao().observeUrls()

    suspend fun toggleSaved(article: ArticleEntity): Boolean =
        (extensions.saveArticle ?: error("Сохранение статей не подключено"))(db, article)
    fun observeFeed(sourceIds: List<Long>?): Flow<List<ArticleEntity>> =
        db.articleDao().observeFiltered(sourceIds.orEmpty(), sourceIds != null)

    suspend fun seedDefaults() = extensions.seed(db, settings)
    fun feedOptions(sources: List<SourceEntity>, category: FeedCategory) = extensions.filterOptions(sources, category)
    fun filterSourceIds(sources: List<SourceEntity>, category: FeedCategory, keys: Set<String>): List<Long>? =
        if (category == FeedCategory.ALL && keys.isEmpty()) null else feedOptions(sources, category)
            .filter { keys.isEmpty() || it.key in keys }.flatMap { it.ids }.distinct().sorted()

    suspend fun addSource(name: String, kind: String, rawAddress: String): Boolean {
        require(name.isNotBlank()) { "Укажи название источника" }
        val address = sourcePlugins.canonicalAddress(rawAddress, kind)
        return db.sourceDao().insert(SourceEntity(name = name.trim().take(70), kind = kind, address = address)) != -1L
    }

    suspend fun discover(provider: io.newsbrief.app.network.DirectoryProvider, query: String, offset: Int = 0): io.newsbrief.app.network.DirectoryResults =
        directory?.invoke(provider, query, offset) ?: io.newsbrief.app.network.DirectoryResults(emptyList())

    private val directory = extensions.directory?.invoke(db, directoryNetwork)

    suspend fun addDiscoveredSource(source: io.newsbrief.app.network.DirectorySource): Boolean {
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

    suspend fun toggleSource(id: Long, enabled: Boolean) = db.sourceDao().setEnabled(id, enabled)
    suspend fun removeSource(id: Long) = db.sourceDao().delete(id)

    private val feedLoader = ManualFeedLoader(db, collector, onFeedUpdated)
    private val refreshAction = extensions.refresh?.invoke(db, collector, settings, clock, feedLoader)
        ?: feedLoader::refresh
    suspend fun refresh(): RefreshResult {
        seedDefaults()
        return refreshAction()
    }

    private suspend fun mergePluginPost(incoming: ArticleEntity) {
        val existing = db.articleDao().findByLink(incoming.link) ?: return
        if (existing.sourceId != incoming.sourceId) return
        val merged = sourcePlugins.merge(existing, incoming)
        if (merged != existing) db.articleDao().updateArticle(merged)
    }

    override suspend fun lookup(url: String): ArticleEntity? = db.articleDao().findByLink(url)

    override suspend fun prefetchCandidates(): List<ArticleEntity> =
        db.articleDao().recent(System.currentTimeMillis() - 48 * 60 * 60_000L, 600)

    override suspend fun linkedArticle(parent: ArticleEntity, url: String): ArticleEntity {
        val saved = lookup(url) ?: ArticleEntity(sourceId = 0, sourceName = parent.sourceName,
            title = parent.title, excerpt = "", link = url, publishedAt = parent.publishedAt)
        val linked = saved.copy(proxyRequired = saved.proxyRequired || parent.proxyRequired ||
            io.newsbrief.app.network.ArticleLinks.isTelegramPost(parent))
        if (linked.proxyRequired && !saved.proxyRequired && saved.id != 0L) db.articleDao().requireProxy(url)
        return linked
    }

    override suspend fun readArticle(article: ArticleEntity, refresh: Boolean, preserveLongerText: Boolean): ReaderArticle {
        val stored = db.articleDao().findByLink(article.link) ?: article
        val saved = stored.copy(proxyRequired = stored.proxyRequired || article.proxyRequired)
        val loaded = articleReader?.load(saved, refresh) ?: return ReaderArticle(saved.title,
            saved.content.ifBlank { saved.excerpt }, saved.imageUrl, saved.link, publishedAt = saved.publishedAt,
            author = saved.author, images = io.newsbrief.app.network.ArticleImages.decode(saved.imagesJson),
            links = io.newsbrief.app.network.ArticleLinks.decode(saved.linksJson))
        val result = if (preserveLongerText && saved.content.length > loaded.text.length)
            loaded.copy(text = saved.content) else loaded
        if (result.notice == null && result.text.isNotBlank()) {
            db.articleDao().insertAll(listOf(saved))
            if (saved.proxyRequired) db.articleDao().requireProxy(saved.link)
            db.articleDao().updateReader(article.link, result.title, result.text, result.imageUrl,
                result.publishedAt ?: saved.publishedAt, result.author, org.json.JSONArray(result.images).toString(), io.newsbrief.app.network.ArticleLinks.encode(result.links))
        }
        return result
    }

    suspend fun digest(mode: String): DigestEntity? = db.digestDao().find(LocalDate.now().toString(), mode)

    suspend fun generateDigest(mode: String): DigestEntity {
        require(mode == DigestMode.SHORT || mode == DigestMode.FULL)
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1_000
        val recent = db.articleDao().recent(cutoff, 600)
        val digest = (extensions.generate ?: error("ИИ-сводки пока не подключены"))(recent, mode, settings)
        db.digestDao().save(digest)
        return digest
    }
}
