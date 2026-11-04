package io.newsbrief.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadStateStorageTest {
    @Test fun unreadFilteringHappensBeforeTheFeedLimitAndSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "read-state-test.sqlite"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, NewsDatabase::class.java, name).build()
        val source = db.sourceDao().insert(SourceEntity(name = "Feed", address = "https://example.org/feed", kind = SourceKind.RSS))
        val rows = (1..260).map { n -> ArticleEntity(sourceId = source, sourceName = "Feed", title = "Post $n", excerpt = "",
            link = "https://example.org/$n", publishedAt = n.toLong(), readAt = if (n > 2) 100L else null) }
        val ids = db.articleDao().insertAll(rows)
        assertEquals(setOf("Post 1", "Post 2"), db.articleDao().observeUnreadFiltered(emptyList(), false).first().map { it.title }.toSet())
        db.articleDao().markVisibleRead(listOf(ids[0]), 200L)
        assertEquals(listOf("Post 2"), db.articleDao().observeUnreadFiltered(emptyList(), false).first().map { it.title })
        val pinned = db.articleDao().observeUnreadFiltered(emptyList(), false, listOf(ids[0])).first().map { it.title }.toSet()
        assertEquals(setOf("Post 1", "Post 2"), pinned)
        db.sourceDao().updateFetchProgress(source, "latest", "", false)
        db.sourceDao().updateFetchProgress(source, "stale", "", true)
        db.close()
        val reopened = Room.databaseBuilder(context, NewsDatabase::class.java, name).build()
        try {
            assertEquals(200L, reopened.articleDao().findByLink("https://example.org/1")!!.readAt)
            val storedSource = reopened.sourceDao().observe().first().single()
            assertEquals("latest", storedSource.fetchCursor)
            assertFalse(storedSource.historyGap)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
    @Test fun unreadCountTracksReadStateFiltersAndAllStoredArticles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NewsDatabase::class.java).build()
        try {
            val first = db.sourceDao().insert(SourceEntity(name = "First", address = "https://first.example/feed", kind = SourceKind.RSS))
            val second = db.sourceDao().insert(SourceEntity(name = "Second", address = "https://second.example/feed", kind = SourceKind.RSS))
            val rows = (1..260).map { n -> ArticleEntity(sourceId = first, sourceName = "First", title = "Post $n", excerpt = "", link = "https://first.example/$n", publishedAt = n.toLong()) } +
                ArticleEntity(sourceId = second, sourceName = "Second", title = "Other", excerpt = "", link = "https://second.example/1", publishedAt = 1)
            db.articleDao().insertAll(rows)
            assertEquals(mapOf(first to 260, second to 1), db.articleDao().observeUnreadCountsBySource().first().associate { it.sourceId to it.unreadCount })
            assertEquals(261, db.articleDao().observeUnreadCount(emptyList(), false, false).first())
            assertEquals(260, db.articleDao().observeUnreadCount(listOf(first), true, false).first())
            assertEquals(0, db.articleDao().observeUnreadCount(emptyList(), true, false).first())
            db.articleDao().setReadAt("https://first.example/1", 100)
            assertEquals(259, db.articleDao().observeUnreadCountsBySource().first().single { it.sourceId == first }.unreadCount)
            assertEquals(259, db.articleDao().observeUnreadCount(listOf(first), true, false).first())
            db.savedArticleDao().insert(SavedArticleEntity("https://second.example/1"))
            db.sourceDao().setEnabled(second, false)
            assertEquals(1, db.articleDao().observeUnreadCount(emptyList(), false, true).first())
            assertEquals(259, db.articleDao().observeUnreadCount(emptyList(), false, false).first())
        } finally { db.close() }
    }

}
