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
    @Test fun realTelegramTechnologyCategorySupportsOverTenThousandSources() = runBlocking {
        val result = PublicDirectoryNetwork().search(DirectoryProvider.TELEGRAM, "technology")
        assertTrue(result.items.isNotEmpty())
        assertTrue(result.items.all { it.kind == SourceKind.TELEGRAM && it.address.startsWith("https://t.me/s/") })
        assertTrue("Category size: ${result.total}", (result.total ?: 0) > 10_000)
        println("TGden technology: ${result.items.size} public channels returned, ${result.total} tagged records")
    }
    @Test fun aiCategoryHasManyResultsAndSecondPageAddsDifferentChannels() = runBlocking {
        val network = PublicDirectoryNetwork()
        val first = network.search(DirectoryProvider.TELEGRAM, "artificial intelligence")
        assertTrue((first.total ?: 0) > 1000)
        assertTrue(first.items.size > 3)
        val next = network.more(DirectoryProvider.TELEGRAM, "artificial intelligence", first.nextOffset!!)
        val merged = TelegramTopics.merge(first, next)
        assertTrue(merged.items.size > first.items.size)
        val useful = merged.items.filter { TelegramTopics.show(it, true) }
        assertTrue(useful.size > 10)
        assertTrue(useful.any { "news" in it.topics })
        println("TGden AI: ${first.total} tagged records; ${merged.items.size} public channels from two pages; ${useful.size} after crypto/gaming filter")
    }
    @Test fun telegramKeywordSearchRespectsQueryInsteadOfReturningUnrelatedTopChannels() = runBlocking {
        val network = PublicDirectoryNetwork()
        val matches = network.search(DirectoryProvider.TELEGRAM, "ChatGPT")
        assertTrue((matches.total ?: 0) > 3)
        assertTrue(matches.items.any { it.title.contains("ChatGPT", true) })
        assertTrue(network.search(DirectoryProvider.TELEGRAM, "thisshouldnotmatchzz987").items.isEmpty())
        println("TGden ChatGPT: ${matches.total} matches, ${matches.items.size} public channels returned")
    }
    @Test fun discoveredTechnologyFeedImportsThroughTheRealCollectorAndRoom() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val source = PublicDirectoryNetwork().search(DirectoryProvider.RSS, "technology").items.first {
                it.address.contains("theguardian.com/technology/rss")
            }
            val repo = TestNewsRepository(db, WebCollectors(), SettingsStore(app))
            assertTrue(repo.addDiscoveredSource(source))
            val saved = db.sourceDao().enabled().single()
            assertTrue(FeedCategory.TECH in FeedFilters.categories(saved))
            val articles = repo.observeLatest().first()
            assertTrue(articles.isNotEmpty())
            assertTrue(articles.all { it.sourceId == saved.id })
            println("Live RSS discovery → verified source → Room feed: ${articles.size} articles")
        } finally { db.close() }
    }
    @Test fun discoveredAiNewsChannelHasReadablePosts() = runBlocking {
        val source = PublicDirectoryNetwork().search(DirectoryProvider.TELEGRAM, "ИИ").items.first {
            it.address.endsWith("/hiaimedia")
        }
        val items = WebCollectors().collect(SourceEntity(name = source.title, address = source.address, kind = source.kind))
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.content.length > 100 })
        println("Discovered Hi, AI channel: ${items.size} readable Telegram posts")
    }

}
