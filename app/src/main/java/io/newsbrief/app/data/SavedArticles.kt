package io.newsbrief.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomSavedArticleDao : SavedArticleDao {
    @Query("SELECT link FROM saved_articles")
    override fun observeUrls(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE link = :url)")
    override suspend fun contains(url: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    override suspend fun insert(item: SavedArticleEntity)
    @Query("DELETE FROM saved_articles WHERE link = :url")
    override suspend fun remove(url: String)
}
