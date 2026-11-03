package io.newsbrief.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomSourceDao : SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    override fun observe(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY id")
    override suspend fun enabled(): List<SourceEntity>

    @Query("SELECT COUNT(*) FROM sources")
    override suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    override suspend fun insert(source: SourceEntity): Long

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    override suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE sources SET fetchCursor = COALESCE(:cursor, fetchCursor), historyGap = :historyGap WHERE id = :id AND fetchCursor = :expected")
    override suspend fun updateFetchProgress(id: Long, cursor: String?, expected: String, historyGap: Boolean)

    @Query("DELETE FROM sources WHERE id = :id")
    override suspend fun delete(id: Long)
}

@Dao
interface RoomArticleDao : ArticleDao {
    @Query("SELECT a.* FROM articles a INNER JOIN saved_articles b ON b.link = a.link ORDER BY b.savedAt DESC, a.id DESC")
    override fun observeSaved(): Flow<List<ArticleEntity>>

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND a.publishedAt >= :from ORDER BY a.publishedAt DESC LIMIT 250")
    override fun observeRecent(from: Long): Flow<List<ArticleEntity>>

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND (:filtered = 0 OR a.sourceId IN (:sourceIds)) " +
        "ORDER BY a.publishedAt DESC LIMIT 250")
    override fun observeFiltered(sourceIds: List<Long>, filtered: Boolean): Flow<List<ArticleEntity>>

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND (a.readAt IS NULL OR a.id IN (:includeIds)) AND (:filtered = 0 OR a.sourceId IN (:sourceIds)) " +
        "ORDER BY a.publishedAt DESC LIMIT 250")
    override fun observeUnreadFiltered(sourceIds: List<Long>, filtered: Boolean, includeIds: List<Long>): Flow<List<ArticleEntity>>

    @Query("UPDATE articles SET readAt = :readAt WHERE id IN (:ids) AND readAt IS NULL")
    override suspend fun markVisibleRead(ids: List<Long>, readAt: Long)

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND a.publishedAt >= :from ORDER BY a.publishedAt DESC LIMIT :limit")
    override suspend fun recent(from: Long, limit: Int): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    override suspend fun insertAll(items: List<ArticleEntity>): List<Long>

    @Update
    override suspend fun updateArticle(article: ArticleEntity)

    @Query("SELECT * FROM articles WHERE link = :url LIMIT 1")
    override suspend fun findByLink(url: String): ArticleEntity?

    @Query("UPDATE articles SET sourcePluginId = :pluginId WHERE link = :url AND sourcePluginId = ''")
    override suspend fun fillSourcePlugin(url: String, pluginId: String)

    @Query("UPDATE articles SET readAt = :readAt WHERE link = :url")
    override suspend fun setReadAt(url: String, readAt: Long?)

    @Query("UPDATE articles SET proxyRequired = 1 WHERE link = :url")
    override suspend fun requireProxy(url: String)

    @Query("UPDATE articles SET title = :title, content = :content, imageUrl = :imageUrl, " +
        "publishedAt = :publishedAt, author = :author, imagesJson = :imagesJson, linksJson = :linksJson, readerLoaded = 1 WHERE link = :url")
    override suspend fun updateReader(url: String, title: String, content: String, imageUrl: String,
        publishedAt: Long, author: String, imagesJson: String, linksJson: String)

    @Query("UPDATE articles SET content = CASE WHEN content = '' THEN :content ELSE content END, " +
        "imageUrl = CASE WHEN imageUrl = '' THEN :imageUrl ELSE imageUrl END, " +
        "linksJson = CASE WHEN :linksJson != '[]' THEN :linksJson ELSE linksJson END WHERE link = :url")
    override suspend fun fillMissingPreview(url: String, content: String, imageUrl: String, linksJson: String)

    @Query("UPDATE articles SET sourceId = :sourceId, sourceName = :sourceName, sourcePluginId = CASE WHEN :sourcePluginId != '' THEN :sourcePluginId ELSE sourcePluginId END WHERE link = :url AND " +
        "NOT EXISTS (SELECT 1 FROM sources WHERE id = articles.sourceId AND enabled = 1)")
    override suspend fun associateEnabledSource(url: String, sourceId: Long, sourceName: String, sourcePluginId: String)

    @Query("DELETE FROM articles WHERE publishedAt < :cutoff AND NOT EXISTS (SELECT 1 FROM saved_articles b WHERE b.link = articles.link)")
    override suspend fun deleteOld(cutoff: Long)
}

@Dao
interface RoomDigestDao : DigestDao {
    @Query("SELECT * FROM digests WHERE date = :date AND mode = :mode LIMIT 1")
    override suspend fun find(date: String, mode: String): DigestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun save(digest: DigestEntity)
}

@Dao
interface RoomCatalogSearchDao : CatalogSearchDao {
    @Query("SELECT * FROM catalog_searches WHERE queryKey = :key LIMIT 1")
    override suspend fun find(key: String): CatalogSearchEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun save(search: CatalogSearchEntity)
    @Query("DELETE FROM catalog_searches WHERE queryKey NOT IN (SELECT queryKey FROM catalog_searches ORDER BY fetchedAt DESC LIMIT 50)")
    override suspend fun prune()
}

@Database(entities = [SourceEntity::class, ArticleEntity::class, DigestEntity::class, CatalogSearchEntity::class, SavedArticleEntity::class], version = 10, exportSchema = true)
abstract class NewsDatabase : RoomDatabase(), NewsStore {
    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction { block() }
    abstract override fun sourceDao(): RoomSourceDao
    abstract override fun articleDao(): RoomArticleDao
    abstract override fun digestDao(): RoomDigestDao
    abstract override fun catalogSearchDao(): RoomCatalogSearchDao
    abstract override fun savedArticleDao(): RoomSavedArticleDao
}
