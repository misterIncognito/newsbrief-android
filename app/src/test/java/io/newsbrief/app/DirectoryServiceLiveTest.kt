package io.newsbrief.app

import io.newsbrief.app.network.*
import io.newsbrief.app.data.*
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in network checks: -PverifyDirectoryServices=true --tests '*DirectoryServiceLiveTest'. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DirectoryServiceLiveTest {
    @Test fun realRssDirectoryReturnsDiscoverableFeeds() = runBlocking {
        val result = PublicDirectoryNetwork().search(DirectoryProvider.RSS, "technology")
        assertTrue(result.items.isNotEmpty())
        assertTrue(result.items.all { it.kind == SourceKind.RSS && it.address.startsWith("https://") })
        println("Feedly live: ${result.items.size} feeds returned and parsed")
    }
    @Test fun realTelegramDirectoryContainsOverTenThousandChannels() = runBlocking {
        val result = PublicDirectoryNetwork().search(DirectoryProvider.TELEGRAM, "technology")
        assertTrue(result.items.isNotEmpty())
        assertTrue(result.items.all { it.kind == SourceKind.TELEGRAM && it.address.startsWith("https://t.me/s/") })
        assertTrue("Live directory size: ${result.directorySize}", (result.directorySize ?: 0) > 10_000)
        println("TGX live: ${result.items.size} returned, ${result.total} matches, ${result.directorySize} indexed channels")
    }
    @Test fun discoveredTechnologyFeedImportsThroughTheRealCollectorAndRoom() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val source = PublicDirectoryNetwork().search(DirectoryProvider.RSS, "technology").items.first {
                it.address.contains("theguardian.com/technology/rss")
            }
            val repo = NewsRepository(db, WebCollectors(), SettingsStore(app))
            assertTrue(repo.addDiscoveredSource(source))
            val saved = db.sourceDao().enabled().single()
            assertTrue(FeedCategory.TECH in FeedFilters.categories(saved))
            val articles = repo.observeLatest().first()
            assertTrue(articles.isNotEmpty())
            assertTrue(articles.all { it.sourceId == saved.id })
            println("Live RSS discovery → verified source → Room feed: ${articles.size} articles")
        } finally { db.close() }
    }
}
