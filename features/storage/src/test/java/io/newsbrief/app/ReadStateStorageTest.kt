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
}
