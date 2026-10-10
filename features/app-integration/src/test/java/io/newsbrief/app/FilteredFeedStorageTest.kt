package io.newsbrief.app

import android.app.Application
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
@Config(sdk = [35], application = Application::class)
class FilteredFeedStorageTest {
    @Test fun filterRunsBeforeGlobalLimitAndKeepsOfflineArticlesAccessible() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val general = db.sourceDao().insert(SourceEntity(name = "General", address = "https://general.example/rss", kind = SourceKind.RSS))
            val tech = db.sourceDao().insert(SourceCatalog.presets.first { it.name == "The Verge" }.asEntity())
            val articles = (1..300).map { i -> ArticleEntity(sourceId = general, sourceName = "General",
                title = "General $i", excerpt = "", link = "https://general.example/$i", publishedAt = 10000 + i.toLong()) }
            val saved = ArticleEntity(sourceId = tech, sourceName = "The Verge", title = "Older tech",
                excerpt = "", content = "Offline text", link = "https://www.theverge.com/older", publishedAt = 1)
            db.articleDao().insertAll(articles + saved)
            val repo = NewsRepository(db, object : io.newsbrief.app.network.NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, SettingsStore(app))
            assertEquals(250, repo.observeFeed(null).first().size)
            assertFalse(repo.observeFeed(null).first().any { it.link == saved.link })
            assertEquals("Offline text", repo.observeFeed(listOf(tech)).first().single().content)
            assertTrue(repo.observeFeed(emptyList()).first().isEmpty())
            db.sourceDao().setEnabled(tech, false)
            assertTrue(repo.observeFeed(listOf(tech)).first().isEmpty())
            assertEquals("Offline text", repo.lookup(saved.link)!!.content)
        } finally { db.close() }
    }
}
