package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderLinksStorageTest {
    @Test fun refreshBackfillsOldPostLinksWithoutReplacingSavedBody() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val settings = SettingsStore(app).apply {
                markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
                markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
            }
            val source = SourceEntity(name = "dev.by", address = "https://t.me/s/devby", kind = SourceKind.TELEGRAM)
            val id = db.sourceDao().insert(source)
            val post = ArticleEntity(sourceId = id, sourceName = source.name, title = "Saved", excerpt = "Saved",
                content = "Saved full body", readerLoaded = true, link = "https://t.me/devby/16806", publishedAt = 123)
            db.articleDao().insertAll(listOf(post))
            val links = ArticleLinks.encode(listOf(ArticleLink("Подробнее", "https://devby.io/news/story")))
            val repo = TestNewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = listOf(post.copy(content = "Preview", linksJson = links))
            }, settings)
            assertEquals(0, repo.refresh().added)
            val saved = repo.lookup(post.link)!!
            assertEquals("Saved full body", saved.content)
            assertEquals(links, saved.linksJson)
            val offline = repo.readArticle(saved)
            assertNull(offline.notice)
            assertEquals("https://devby.io/news/story", offline.links.single().url)
        } finally { db.close() }
    }
}
