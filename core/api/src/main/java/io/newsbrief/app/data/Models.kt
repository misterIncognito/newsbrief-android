package io.newsbrief.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object SourceKind {
    const val RSS = "RSS"
    const val TELEGRAM = "TELEGRAM"
    const val TELEGRAM_PROXY = "TELEGRAM_PROXY"
}

@Entity(tableName = "sources", indices = [Index(value = ["address"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val kind: String,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "'[]'") val categoriesJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val fetchCursor: String = ""
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
    @ColumnInfo(defaultValue = "'[]'") val linksJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val proxyRequired: Boolean = false,
    @ColumnInfo(defaultValue = "''") val sourcePluginId: String = "",
    val link: String,
    val publishedAt: Long,
    val importedAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null
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


@Entity(tableName = "catalog_searches")
data class CatalogSearchEntity(@PrimaryKey val queryKey: String, val responseJson: String, val fetchedAt: Long)


@Entity(tableName = "saved_articles")
data class SavedArticleEntity(@PrimaryKey val link: String, val savedAt: Long = System.currentTimeMillis())

