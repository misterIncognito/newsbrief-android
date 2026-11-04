package io.newsbrief.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Session storage used until the storage feature is added. It never opens existing files. */
class MemoryNewsStore : NewsStore {
    private data class State(val sources: List<SourceEntity> = emptyList(), val articles: List<ArticleEntity> = emptyList(),
        val saved: List<SavedArticleEntity> = emptyList(), val digests: List<DigestEntity> = emptyList(), val searches: List<CatalogSearchEntity> = emptyList())
    private class Transaction(val store: MemoryNewsStore) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Transaction>
    }
    private val mutex = Mutex()
    private val lock = Any()
    private var nextSourceId = 1L
    private var nextArticleId = 1L
    private val state = MutableStateFlow(State())
    private suspend fun <T> access(block: () -> T): T =
        if (currentCoroutineContext()[Transaction]?.store === this) synchronized(lock) { block() } else mutex.withLock { synchronized(lock) { block() } }
    override suspend fun <T> transaction(block: suspend () -> T): T {
        if (currentCoroutineContext()[Transaction]?.store === this) return block()
        return mutex.withLock {
            val before = state.value
            val ids = nextSourceId to nextArticleId
            try { withContext(Transaction(this)) { block() } }
            catch (e: Throwable) { state.value = before; nextSourceId = ids.first; nextArticleId = ids.second; throw e }
        }
    }
    private fun State.visible(from: Long = 0) = articles.filter { a -> a.publishedAt >= from && sources.any { it.id == a.sourceId && it.enabled } }
        .sortedByDescending { it.publishedAt }
    private val sources = object : SourceDao {
        override fun observe() = state.map { it.sources.sortedBy { source -> source.id } }
        override suspend fun enabled() = access { state.value.sources.filter { it.enabled }.sortedBy { it.id } }
        override suspend fun count() = access { state.value.sources.size }
        override suspend fun insert(source: SourceEntity) = access {
            val old = state.value
            if (old.sources.any { it.address == source.address || source.id != 0L && it.id == source.id }) -1L else {
                val id = if (source.id != 0L) source.id else nextSourceId
                nextSourceId = maxOf(nextSourceId, id + 1); state.value = old.copy(sources = old.sources + source.copy(id = id)); id
            }
        }
        override suspend fun setEnabled(id: Long, enabled: Boolean) = access {
            state.value = state.value.copy(sources = state.value.sources.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
        override suspend fun updateFetchProgress(id: Long, cursor: String?, expected: String, historyGap: Boolean) = access {
            state.value = state.value.copy(sources = state.value.sources.map { if (it.id == id && it.fetchCursor == expected) it.copy(fetchCursor = cursor ?: it.fetchCursor, historyGap = historyGap) else it })
        }
        override suspend fun delete(id: Long) = access { state.value = state.value.copy(sources = state.value.sources.filterNot { it.id == id }) }
    }
    private suspend fun update(url: String, change: (ArticleEntity) -> ArticleEntity) = access {
        state.value = state.value.copy(articles = state.value.articles.map { if (it.link == url) change(it) else it })
    }
    private val articles = object : ArticleDao {
        override fun observeSaved() = state.map { s -> s.saved.sortedByDescending { it.savedAt }.mapNotNull { saved -> s.articles.find { it.link == saved.link } } }
        override fun observeRecent(from: Long) = state.map { it.visible(from).take(250) }
        override fun observeFiltered(sourceIds: List<Long>, filtered: Boolean) = state.map {
            it.visible().filter { a -> !filtered || a.sourceId in sourceIds }.take(250)
        }
        override fun observeSourceArticleCounts() = state.map { s ->
            s.visible().groupingBy { it.sourceId }.eachCount().map { (id, count) -> SourceArticleCount(id, count) }
        }
        override fun observeUnreadFiltered(sourceIds: List<Long>, filtered: Boolean, includeIds: List<Long>) = state.map {
            it.visible().filter { a -> (a.readAt == null || a.id in includeIds) && (!filtered || a.sourceId in sourceIds) }.take(250)
        }
        override suspend fun markVisibleRead(ids: List<Long>, readAt: Long) = access {
            state.value = state.value.copy(articles = state.value.articles.map {
                if (it.id in ids && it.readAt == null) it.copy(readAt = readAt) else it
            })
        }
        override suspend fun recent(from: Long, limit: Int) = access { state.value.visible(from).take(limit) }
        override suspend fun insertAll(items: List<ArticleEntity>) = access {
            items.map { article ->
                val old = state.value
                if (old.articles.any { it.link == article.link || article.id != 0L && it.id == article.id }) -1L else {
                    val id = if (article.id != 0L) article.id else nextArticleId
                    nextArticleId = maxOf(nextArticleId, id + 1); state.value = old.copy(articles = old.articles + article.copy(id = id)); id
                }
            }
        }
        override suspend fun updateArticle(article: ArticleEntity) = update(article.link) { article }
        override fun observeByLink(url: String) = state.map { it.articles.find { article -> article.link == url } }
        override suspend fun findByLink(url: String) = access { state.value.articles.find { it.link == url } }
        override suspend fun fillSourcePlugin(url: String, pluginId: String) = update(url) { if (it.sourcePluginId.isEmpty()) it.copy(sourcePluginId = pluginId) else it }
        override suspend fun setReadAt(url: String, readAt: Long?) = update(url) { it.copy(readAt = readAt) }
        override suspend fun requireProxy(url: String) = update(url) { it.copy(proxyRequired = true) }
        override suspend fun updateReader(url: String, title: String, content: String, imageUrl: String, publishedAt: Long,
            author: String, imagesJson: String, linksJson: String) = update(url) {
            it.copy(title = title, content = content, imageUrl = imageUrl, publishedAt = publishedAt, author = author,
                imagesJson = imagesJson, linksJson = linksJson, readerLoaded = true)
        }
        override suspend fun fillMissingPreview(url: String, content: String, imageUrl: String, linksJson: String) = update(url) {
            it.copy(content = it.content.ifBlank { content }, imageUrl = it.imageUrl.ifBlank { imageUrl },
                linksJson = if (linksJson != "[]") linksJson else it.linksJson)
        }
        override suspend fun associateEnabledSource(url: String, sourceId: Long, sourceName: String, sourcePluginId: String) = update(url) {
            if (state.value.sources.any { s -> s.id == it.sourceId && s.enabled }) it else it.copy(sourceId = sourceId,
                sourceName = sourceName, sourcePluginId = sourcePluginId.ifBlank { it.sourcePluginId })
        }
        override suspend fun deleteOld(cutoff: Long) = access { val s = state.value
            state.value = s.copy(articles = s.articles.filter { it.publishedAt >= cutoff || s.saved.any { saved -> saved.link == it.link } })
        }
    }
    private val digests = object : DigestDao {
        override suspend fun find(date: String, mode: String) = access { state.value.digests.find { it.date == date && it.mode == mode } }
        override suspend fun save(digest: DigestEntity) = access { val s = state.value
            state.value = s.copy(digests = s.digests.filterNot { it.date == digest.date && it.mode == digest.mode } + digest)
        }
    }
    private val searches = object : CatalogSearchDao {
        override suspend fun find(key: String) = access { state.value.searches.find { it.queryKey == key } }
        override suspend fun save(search: CatalogSearchEntity) = access { val s = state.value
            state.value = s.copy(searches = s.searches.filterNot { it.queryKey == search.queryKey } + search)
        }
        override suspend fun prune() = access { state.value = state.value.copy(searches = state.value.searches.sortedByDescending { it.fetchedAt }.take(50)) }
    }
    private val saved = object : SavedArticleDao {
        override fun observeUrls() = state.map { it.saved.map { saved -> saved.link } }
        override suspend fun contains(url: String) = access { state.value.saved.any { it.link == url } }
        override suspend fun insert(item: SavedArticleEntity) = access {
            if (state.value.saved.none { it.link == item.link }) state.value = state.value.copy(saved = state.value.saved + item)
        }
        override suspend fun remove(url: String) = access { state.value = state.value.copy(saved = state.value.saved.filterNot { it.link == url }) }
    }
    override fun sourceDao(): SourceDao = sources
    override fun articleDao(): ArticleDao = articles
    override fun digestDao(): DigestDao = digests
    override fun catalogSearchDao(): CatalogSearchDao = searches
    override fun savedArticleDao(): SavedArticleDao = saved
}
