package io.newsbrief.app.data

import io.newsbrief.app.ai.AiClient
import io.newsbrief.app.network.ArticleReader
import io.newsbrief.app.network.ReaderArticle
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.util.FeedRules
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NewsRepository(
    private val db: NewsDatabase,
    private val collector: NewsCollector,
    private val settings: SettingsStore,
    directoryNetwork: io.newsbrief.app.network.DirectoryNetwork = io.newsbrief.app.network.PublicDirectoryNetwork(),
    private val articleReader: ArticleReader = ArticleReader(settings)
) {
    private val ai = AiClient(settings)
    private val refreshMutex = Mutex()

    data class RefreshResult(val added: Int, val sources: Int, val errors: List<String>)

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observe()
    fun observeLatest(): Flow<List<ArticleEntity>> = db.articleDao().observeRecent(0)
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
        val address = FeedRules.canonicalAddress(rawAddress, kind)
        return db.sourceDao().insert(SourceEntity(name = name.trim().take(70), kind = kind, address = address)) != -1L
    }

    suspend fun discover(provider: io.newsbrief.app.network.DirectoryProvider, query: String, offset: Int = 0): io.newsbrief.app.network.DirectoryResults =
        directory.search(provider, query, offset)

    private val directory = SourceDirectoryRepository(db.catalogSearchDao(), directoryNetwork)

    suspend fun addDiscoveredSource(source: io.newsbrief.app.network.DirectorySource): Boolean {
        val address = FeedRules.canonicalAddress(source.address, source.kind)
        val candidate = SourceEntity(name = source.title, address = address, kind = source.kind,
            categoriesJson = org.json.JSONArray(source.categories.map { it.name }).toString())
        val items = collector.collect(candidate).filter {
            it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
        }
        require(items.isNotEmpty()) { "Источник не отдаёт доступных публикаций. Попробуй другой результат." }
        return db.withTransaction {
            val id = db.sourceDao().insert(candidate)
            if (id == -1L) return@withTransaction false
            val articles = items.map { it.copy(sourceId = id, sourceName = candidate.name) }
            val inserted = db.articleDao().insertAll(articles)
            articles.zip(inserted).forEach { (article, insertedId) ->
                if (insertedId == -1L) {
                    db.articleDao().associateEnabledSource(article.link, id, candidate.name)
                    db.articleDao().fillMissingPreview(article.link, article.content, article.imageUrl, article.linksJson)
                }
            }
            true
        }
    }

    suspend fun toggleSource(id: Long, enabled: Boolean) = db.sourceDao().setEnabled(id, enabled)
    suspend fun removeSource(id: Long) = db.sourceDao().delete(id)

    suspend fun refresh(): RefreshResult = refreshMutex.withLock {
        seedDefaults()
        // Fetch newly added sources first so slow existing feeds do not delay them.
        val sources = db.sourceDao().enabled().sortedByDescending { it.id }
        var total = 0
        val errors = mutableListOf<String>()
        for (source in sources) {
            try {
                val items = collector.collect(source).filter {
                    // Reject broken/future timestamps (5 min of clock skew allowed).
                    it.publishedAt <= System.currentTimeMillis() + 5 * 60_000L
                }
                // Cross-feed duplicate links are ignored by unique SQLite index.
                val results = db.articleDao().insertAll(items)
                total += results.count { it != -1L }
                // Existing v0.3 articles had no image/full-text columns. Backfill
                // metadata from refreshed feeds without overwriting saved reader text.
                items.zip(results).forEach { (item, id) ->
                    if (id == -1L) {
                        db.articleDao().associateEnabledSource(item.link, item.sourceId, item.sourceName)
                        if (item.content.isNotBlank() || item.imageUrl.isNotBlank() || item.linksJson != "[]") {
                            db.articleDao().fillMissingPreview(item.link, item.content, item.imageUrl, item.linksJson)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                errors += "${source.name}: ${e.message.orEmpty().take(120)}"
            }
        }
        // Do not silently destroy history. A future retention policy can be user-controlled.
        RefreshResult(added = total, sources = sources.size, errors = errors)
    }

    suspend fun lookup(url: String): ArticleEntity? = db.articleDao().findByLink(url)

    suspend fun readArticle(article: ArticleEntity, refresh: Boolean = false): ReaderArticle {
        val stored = db.articleDao().findByLink(article.link) ?: article
        val saved = stored.copy(proxyRequired = stored.proxyRequired || article.proxyRequired)
        val result = articleReader.load(saved, refresh)
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
