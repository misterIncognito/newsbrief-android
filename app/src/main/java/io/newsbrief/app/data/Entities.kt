package io.newsbrief.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

object SourceKind {
    const val RSS = "RSS"
    const val TELEGRAM = "TELEGRAM"
}

@Entity(tableName = "sources", indices = [Index(value = ["address"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val kind: String,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "'[]'") val categoriesJson: String = "[]"
)

@Entity(tableName = "articles", indices = [Index(value = ["link"], unique = true), Index(value = ["publishedAt"])])
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val title: String,
    val excerpt: String,
    @ColumnInfo(defaultValue = "''") val content: String = "",
    @ColumnInfo(defaultValue = "''") val imageUrl: String = "",
    @ColumnInfo(defaultValue = "0") val readerLoaded: Boolean = false,
    @ColumnInfo(defaultValue = "''") val author: String = "",
    @ColumnInfo(defaultValue = "'[]'") val imagesJson: String = "[]",
    val link: String,
    val publishedAt: Long,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "digests", primaryKeys = ["date", "mode"])
data class DigestEntity(
    val date: String,
    val mode: String,
    val content: String,
    val referencesJson: String,
    val articleCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observe(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<SourceEntity>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: SourceEntity): Long

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ArticleDao {
    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND a.publishedAt >= :from ORDER BY a.publishedAt DESC LIMIT 250")
    fun observeRecent(from: Long): Flow<List<ArticleEntity>>

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND (:filtered = 0 OR a.sourceId IN (:sourceIds)) " +
        "ORDER BY a.publishedAt DESC LIMIT 250")
    fun observeFiltered(sourceIds: List<Long>, filtered: Boolean): Flow<List<ArticleEntity>>

    @Query("SELECT a.* FROM articles a INNER JOIN sources s ON s.id = a.sourceId " +
        "WHERE s.enabled = 1 AND a.publishedAt >= :from ORDER BY a.publishedAt DESC LIMIT :limit")
    suspend fun recent(from: Long, limit: Int): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ArticleEntity>): List<Long>

    @Query("SELECT * FROM articles WHERE link = :url LIMIT 1")
    suspend fun findByLink(url: String): ArticleEntity?

    @Query("UPDATE articles SET title = :title, content = :content, imageUrl = :imageUrl, " +
        "publishedAt = :publishedAt, author = :author, imagesJson = :imagesJson, readerLoaded = 1 WHERE link = :url")
    suspend fun updateReader(url: String, title: String, content: String, imageUrl: String,
        publishedAt: Long, author: String, imagesJson: String)

    @Query("UPDATE articles SET content = CASE WHEN content = '' THEN :content ELSE content END, " +
        "imageUrl = CASE WHEN imageUrl = '' THEN :imageUrl ELSE imageUrl END WHERE link = :url")
    suspend fun fillMissingPreview(url: String, content: String, imageUrl: String)

    @Query("UPDATE articles SET sourceId = :sourceId, sourceName = :sourceName WHERE link = :url AND " +
        "NOT EXISTS (SELECT 1 FROM sources WHERE id = articles.sourceId AND enabled = 1)")
    suspend fun associateEnabledSource(url: String, sourceId: Long, sourceName: String)

    @Query("DELETE FROM articles WHERE publishedAt < :cutoff")
    suspend fun deleteOld(cutoff: Long)
}

@Dao
interface DigestDao {
    @Query("SELECT * FROM digests WHERE date = :date AND mode = :mode LIMIT 1")
    suspend fun find(date: String, mode: String): DigestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(digest: DigestEntity)
}

@Entity(tableName = "catalog_searches")
data class CatalogSearchEntity(@PrimaryKey val queryKey: String, val responseJson: String, val fetchedAt: Long)

@Dao
interface CatalogSearchDao {
    @Query("SELECT * FROM catalog_searches WHERE queryKey = :key LIMIT 1")
    suspend fun find(key: String): CatalogSearchEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(search: CatalogSearchEntity)
    @Query("DELETE FROM catalog_searches WHERE queryKey NOT IN (SELECT queryKey FROM catalog_searches ORDER BY fetchedAt DESC LIMIT 50)")
    suspend fun prune()
}

@Database(entities = [SourceEntity::class, ArticleEntity::class, DigestEntity::class, CatalogSearchEntity::class], version = 4, exportSchema = true)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun digestDao(): DigestDao
    abstract fun catalogSearchDao(): CatalogSearchDao
}
