package io.newsbrief.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Feature-facing ports. Room and disk implementations belong to storage. */
interface FeedStore {
    fun sourceDao(): SourceDao
    fun articleDao(): ArticleDao
}
interface NewsStore : FeedStore {
    fun digestDao(): DigestDao
    fun catalogSearchDao(): CatalogSearchDao
    fun savedArticleDao(): SavedArticleDao
    suspend fun <T> transaction(block: suspend () -> T): T
}

interface SourceDao {
    fun observe(): Flow<List<SourceEntity>>

    suspend fun enabled(): List<SourceEntity>

    suspend fun count(): Int

    suspend fun insert(source: SourceEntity): Long

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun delete(id: Long)

    suspend fun updateFetchProgress(id: Long, cursor: String?, expected: String, historyGap: Boolean) { throw UnsupportedOperationException("Source cursors unavailable") }
}

data class UnreadSourceCount(val sourceId: Long, val unreadCount: Int)

interface ArticleDao {
    fun observeSaved(): Flow<List<ArticleEntity>>

    fun observeRecent(from: Long): Flow<List<ArticleEntity>>

    fun observeFiltered(sourceIds: List<Long>, filtered: Boolean): Flow<List<ArticleEntity>>
    fun observeUnreadCountsBySource(): Flow<List<UnreadSourceCount>> =
        observeUnreadFiltered(emptyList(), false).map { articles ->
            articles.groupingBy { it.sourceId }.eachCount().map { (id, count) -> UnreadSourceCount(id, count) }
        }
    fun observeUnreadCount(sourceIds: List<Long>, filtered: Boolean, savedOnly: Boolean): Flow<Int> =
        (if (savedOnly) observeSaved() else observeFiltered(sourceIds, filtered)).map { articles -> articles.count { it.readAt == null } }
    fun observeUnreadFiltered(sourceIds: List<Long>, filtered: Boolean, includeIds: List<Long> = emptyList()): Flow<List<ArticleEntity>> =
        observeFiltered(sourceIds, filtered).map { articles -> articles.filter { it.readAt == null || it.id in includeIds } }

    suspend fun recent(from: Long, limit: Int): List<ArticleEntity>

    suspend fun insertAll(items: List<ArticleEntity>): List<Long>

    suspend fun updateArticle(article: ArticleEntity)

    fun observeByLink(url: String): Flow<ArticleEntity?> =
        observeFiltered(emptyList(), false).map { articles -> articles.firstOrNull { it.link == url } }

    suspend fun findByLink(url: String): ArticleEntity?

    suspend fun fillSourcePlugin(url: String, pluginId: String)

    suspend fun requireProxy(url: String)

    suspend fun markVisibleRead(ids: List<Long>, readAt: Long) { throw UnsupportedOperationException("Read status unavailable") }
    suspend fun setReadAt(url: String, readAt: Long?) { throw UnsupportedOperationException("Read status unavailable") }

    suspend fun updateReader(url: String, title: String, content: String, imageUrl: String,
        publishedAt: Long, author: String, imagesJson: String, linksJson: String = "[]")

    suspend fun fillMissingPreview(url: String, content: String, imageUrl: String, linksJson: String = "[]")

    suspend fun associateEnabledSource(url: String, sourceId: Long, sourceName: String, sourcePluginId: String = "")

    suspend fun deleteOld(cutoff: Long)
}

interface DigestDao {
    suspend fun find(date: String, mode: String): DigestEntity?

    suspend fun save(digest: DigestEntity)
}

interface CatalogSearchDao {
    suspend fun find(key: String): CatalogSearchEntity?
    suspend fun save(search: CatalogSearchEntity)
    suspend fun prune()
}

interface SavedArticleDao {
    fun observeUrls(): Flow<List<String>>
    suspend fun contains(url: String): Boolean
    suspend fun insert(item: SavedArticleEntity)
    suspend fun remove(url: String)
}
