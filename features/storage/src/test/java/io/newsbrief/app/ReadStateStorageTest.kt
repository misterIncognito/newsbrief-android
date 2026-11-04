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
    @Test fun sourceCountsIncludeAllCachedArticlesAndExcludeDisabledSources() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NewsDatabase::class.java).build()
        try {
            val enabled = db.sourceDao().insert(SourceEntity(name = "Enabled", address = "https://enabled.example/feed", kind = SourceKind.RSS))
            val disabled = db.sourceDao().insert(SourceEntity(name = "Disabled", address = "https://disabled.example/feed", kind = SourceKind.RSS, enabled = false))
            val rows = (1..260).map { n -> ArticleEntity(sourceId = enabled, sourceName = "Enabled", title = "Post $n", excerpt = "", link = "https://enabled.example/$n", publishedAt = n.toLong()) } +
                ArticleEntity(sourceId = disabled, sourceName = "Disabled", title = "Hidden", excerpt = "", link = "https://disabled.example/1", publishedAt = 1)
            db.articleDao().insertAll(rows)
            assertEquals(listOf(SourceArticleCount(enabled, 260)), db.articleDao().observeSourceArticleCounts().first())
            db.sourceDao().setEnabled(disabled, true)
            assertEquals(mapOf(enabled to 260, disabled to 1), db.articleDao().observeSourceArticleCounts().first().associate { it.sourceId to it.articleCount })
        } finally { db.close() }
    }

}
