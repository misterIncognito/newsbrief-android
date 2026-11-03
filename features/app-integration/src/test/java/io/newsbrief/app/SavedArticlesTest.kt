package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SavedArticlesTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val emptyCollector = object : NewsCollector {
        override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
    }
    private fun repository(db: NewsDatabase) = TestNewsRepository(db, emptyCollector, SettingsStore(app))
    private fun article(sourceId: Long = 0, link: String = "https://meduza.io/news/story") = ArticleEntity(
        sourceId = sourceId, sourceName = "Publisher", title = "Saved story", excerpt = "Preview", content = "Full offline text",
        readerLoaded = true, author = "Reporter", imageUrl = "https://cdn.example/photo.jpg",
        imagesJson = "[\"https://cdn.example/photo.jpg\"]", linksJson = "[{\"label\":\"Related\",\"url\":\"https://example.org/related\"}]",
        proxyRequired = true, link = link, publishedAt = 123)

    @Test fun savedReaderSurvivesSourceDisableDeleteAndDatabaseReopenWithoutLosingCache() = runBlocking {
        val name = "saved-articles-test.sqlite"
        app.deleteDatabase(name)
        val db = Room.databaseBuilder(app, NewsDatabase::class.java, name).build()
        val saved: ArticleEntity
        try {
            val id = db.sourceDao().insert(SourceEntity(name = "Publisher", address = "https://publisher.example/rss", kind = SourceKind.RSS))
            saved = article(id)
            db.articleDao().insertAll(listOf(saved))
            val repo = repository(db)
            assertTrue(repo.toggleSaved(saved.copy(content = "Stale preview", author = "", proxyRequired = false)))
            repo.toggleSource(id, false)
            assertTrue(repo.observeLatest().first().isEmpty())
            assertEquals(saved.content, repo.observeSaved().first().single().content)
            repo.removeSource(id)
            assertEquals(saved.link, repo.observeSavedUrls().first().single())
        } finally { db.close() }
        val reopened = Room.databaseBuilder(app, NewsDatabase::class.java, name).build()
        try {
            val repo = repository(reopened)
            val cached = repo.observeSaved().first().single()
            assertEquals(saved.content, cached.content)
            assertEquals(saved.author, cached.author)
            assertEquals(saved.imagesJson, cached.imagesJson)
            assertEquals(saved.linksJson, cached.linksJson)
            assertTrue(cached.proxyRequired)
            assertEquals(saved.content, repo.readArticle(cached).text)
            assertFalse(repo.toggleSaved(cached))
            assertTrue(repo.observeSaved().first().isEmpty())
            assertNotNull(repo.lookup(saved.link))
        } finally { reopened.close(); app.deleteDatabase(name) }
    }
    @Test fun publisherReaderWithoutFeedSourceCanBeSavedAndRemovedOffline() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val repo = repository(db)
            val target = article()
            assertTrue(repo.toggleSaved(target))
            assertEquals(target.link, repo.observeSaved().first().single().link)
            assertTrue(repo.observeLatest().first().isEmpty())
            assertFalse(repo.toggleSaved(target))
            assertTrue(repo.observeSavedUrls().first().isEmpty())
            assertEquals(target.content, repo.lookup(target.link)!!.content)
        } finally { db.close() }
    }
    @Test fun savedArticlesAreProtectedFromAgePruning() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val repo = repository(db)
            val saved = article()
            val other = article(link = "https://example.org/not-saved")
            db.articleDao().insertAll(listOf(saved, other))
            repo.toggleSaved(saved)
            db.articleDao().deleteOld(456)
            assertEquals(saved.link, repo.observeSaved().first().single().link)
            assertNotNull(repo.lookup(saved.link))
            assertNull(repo.lookup(other.link))
        } finally { db.close() }
    }
    @Test fun savingTelegramPostPreservesFullTextAndLinks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val repo = repository(db)
            val post = article(link = "https://t.me/channel/123")
            repo.toggleSaved(post)
            val saved = repo.observeSaved().first().single()
            assertEquals(post.content, saved.content)
            assertEquals(post.linksJson, saved.linksJson)
            assertEquals(post.link, saved.link)
        } finally { db.close() }
    }
}
