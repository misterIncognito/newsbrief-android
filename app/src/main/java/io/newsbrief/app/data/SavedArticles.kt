package io.newsbrief.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_articles")
data class SavedArticleEntity(@PrimaryKey val link: String, val savedAt: Long = System.currentTimeMillis())

@Dao
interface SavedArticleDao {
    @Query("SELECT link FROM saved_articles")
    fun observeUrls(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE link = :url)")
    suspend fun contains(url: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SavedArticleEntity)
    @Query("DELETE FROM saved_articles WHERE link = :url")
    suspend fun remove(url: String)
}
