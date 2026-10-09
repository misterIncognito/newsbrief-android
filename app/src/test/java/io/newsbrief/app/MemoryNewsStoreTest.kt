package io.newsbrief.app

import io.newsbrief.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MemoryNewsStoreTest {
    @Test fun failedTransactionRollsBackSourceAndArticleInsertion() = runBlocking {
        val db = MemoryNewsStore()
        try { db.transaction { db.sourceDao().insert(SourceEntity(name = "Test", address = "https://a.test/rss", kind = SourceKind.RSS)); error("cancel insert") } }
        catch (_: IllegalStateException) { }
        assertEquals(0, db.sourceDao().count())
    }
    @Test fun deletedSourceIdIsNotReusedAndOldNewsStaysHidden() = runBlocking {
        val db = MemoryNewsStore(); val dao = db.sourceDao()
        val id = dao.insert(SourceEntity(name = "Old", address = "https://a.test/rss", kind = SourceKind.RSS))
        db.articleDao().insertAll(listOf(ArticleEntity(sourceId = id, sourceName = "Old", title = "Old news", excerpt = "", link = "https://a.test/story", publishedAt = 1)))
        dao.delete(id)
        assertTrue(dao.insert(SourceEntity(name = "New", address = "https://b.test/rss", kind = SourceKind.RSS)) > id)
        assertTrue(db.articleDao().observeRecent(0).first().isEmpty())
    }
    @Test fun savedArticlesStayReadableWhenTheirSourceIsDisabled() = runBlocking {
        val db = MemoryNewsStore()
        val id = db.sourceDao().insert(SourceEntity(name = "Test", address = "https://a.test/rss", kind = SourceKind.RSS))
        val item = ArticleEntity(sourceId = id, sourceName = "Test", title = "Saved", excerpt = "", content = "Offline body", link = "https://a.test/story", publishedAt = 1)
        db.articleDao().insertAll(listOf(item)); db.savedArticleDao().insert(SavedArticleEntity(item.link))
        db.sourceDao().setEnabled(id, false)
        assertTrue(db.articleDao().observeRecent(0).first().isEmpty())
        assertEquals("Offline body", db.articleDao().observeSaved().first().single().content)
    }
}
