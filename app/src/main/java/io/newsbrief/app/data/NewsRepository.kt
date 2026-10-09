package io.newsbrief.app.data

import io.newsbrief.app.ai.AiClient
import io.newsbrief.app.network.ArticleReader
import io.newsbrief.app.network.ReaderArticle
import io.newsbrief.app.network.NewsCollector
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException

class NewsRepository(
    private val db: NewsDatabase,
    private val collector: NewsCollector,
    private val settings: SettingsStore,
    directoryNetwork: io.newsbrief.app.network.DirectoryNetwork = io.newsbrief.app.network.PublicDirectoryNetwork(),
    val sourcePlugins: io.newsbrief.app.network.SourcePluginRegistry = (collector as? io.newsbrief.app.network.WebCollectors)?.plugins
        ?: (collector as? io.newsbrief.app.network.SourcePluginRegistry) ?: io.newsbrief.app.network.SourcePluginRegistry.builtIns(settings),
    private val articleReader: ArticleReader = ArticleReader(settings, telegram = io.newsbrief.app.network.TelegramArticleContent(settings)),
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFeedUpdated: () -> Unit = {}
) : io.newsbrief.app.network.PrefetchArticleStore {
    private val ai = AiClient(settings)

    companion object { const val SYNC_COOLDOWN_MILLIS = io.newsbrief.app.sync.FeedSynchronizer.SYNC_COOLDOWN_MILLIS }

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observe()
    fun observeLatest(): Flow<List<ArticleEntity>> = db.articleDao().observeRecent(0)
    fun observeSaved(): Flow<List<ArticleEntity>> = db.articleDao().observeSaved()
    fun observeSavedUrls(): Flow<List<String>> = db.savedArticleDao().observeUrls()

    suspend fun toggleSaved(article: ArticleEntity): Boolean = db.withTransaction {
        if (db.savedArticleDao().contains(article.link)) {
            db.savedArticleDao().remove(article.link)
            false
        } else {
            // Never replace richer cached reader content with an older feed preview.
            db.articleDao().insertAll(listOf(article))
            db.savedArticleDao().insert(SavedArticleEntity(article.link))
            true
        }
    }
    fun observeFeed(sourceIds: List<Long>?): Flow<List<ArticleEntity>> =
        db.articleDao().observeFiltered(sourceIds.orEmpty(), sourceIds != null)

    suspend fun seedDefaults() {
        val v2Seeded = settings.isSourcesCatalogV2Seeded()
        val v3Seeded = settings.isSourcesCatalogV3Seeded()
        val v4Seeded = settings.isSourcesCatalogV4Seeded()
        val v5Seeded = settings.isSourcesCatalogV5Seeded()
        if (v2Seeded && v3Seeded && v4Seeded && v5Seeded) return
        SourceCatalog.defaultsToSeed(db.sourceDao().count(), v2Seeded, v3Seeded, v4Seeded, v5Seeded)
            .forEach { db.sourceDao().insert(it.asEntity()) }
        if (!v2Seeded) settings.markSourcesCatalogV2Seeded()
        if (!v3Seeded) settings.markSourcesCatalogV3Seeded()
        if (!v4Seeded) settings.markSourcesCatalogV4Seeded()
        if (!v5Seeded) settings.markSourcesCatalogV5Seeded()
    }

    suspend fun addSource(name: String, kind: String, rawAddress: String): Boolean {
        require(name.isNotBlank()) { "Укажи название источника" }
        val address = sourcePlugins.canonicalAddress(rawAddress, kind)
        return db.sourceDao().insert(SourceEntity(name = name.trim().take(70), kind = kind, address = address)) != -1L
    }

    suspend fun discover(provider: io.newsbrief.app.network.DirectoryProvider, query: String, offset: Int = 0): io.newsbrief.app.network.DirectoryResults =
        directory.search(provider, query, offset)

    private val directory = SourceDirectoryRepository(db.catalogSearchDao(), directoryNetwork)

    suspend fun addDiscoveredSource(source: io.newsbrief.app.network.DirectorySource): Boolean {
        val address = sourcePlugins.canonicalAddress(source.address, source.kind)
        val candidate = SourceEntity(name = source.title, address = address, kind = source.kind,
            categoriesJson = org.json.JSONArray(source.categories.map { it.name }).toString())
        val items = collector.collect(candidate).filter {
            it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
        }
        require(items.isNotEmpty()) { "Источник не отдаёт доступных публикаций. Попробуй другой результат." }
        val added = db.withTransaction {
            val id = db.sourceDao().insert(candidate)
            if (id == -1L) return@withTransaction false
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

    private val synchronizer = io.newsbrief.app.sync.FeedSynchronizer(db, collector, settings, clock, onFeedUpdated)
    suspend fun refresh(): io.newsbrief.app.sync.FeedSynchronizer.RefreshResult {
        seedDefaults()
        return synchronizer.refresh()
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
        val loaded = articleReader.load(saved, refresh)
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
        val digest = ai.generate(DigestArticleSelector.pick(recent), mode)
        db.digestDao().save(digest)
        return digest
    }
}
