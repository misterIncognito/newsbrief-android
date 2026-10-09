package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArticlePrefetchTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun settings() = SettingsStore(app).apply {
        markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
        markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
    }
    private class Images : PrefetchImageStore {
        val cached = mutableSetOf<String>()
        override fun contains(url: String) = url in cached
        override suspend fun load(url: String, worker: String): ByteArray { cached += url; return byteArrayOf(1) }
    }
    private val emptyCollector = object : NewsCollector {
        override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
    }
    private suspend fun source(db: NewsDatabase, name: String = "Atom", enabled: Boolean = true): Long =
        db.sourceDao().insert(SourceEntity(name = name, address = "https://example.org/$name", kind = SourceKind.RSS, enabled = enabled))
    private fun article(id: Long, url: String = "https://example.org/story", content: String = "") = ArticleEntity(
        sourceId = id, sourceName = "Source", title = "Story", excerpt = "Preview", content = content,
        link = url, publishedAt = System.currentTimeMillis() - 1000)

    @Test fun atomSummaryRefreshSchedulesPrefetchAndFullTextOpensOfflineBeforeFirstTap() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            source(db)
            var network = 0
            var scheduled = 0
            val collector = object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = FeedParsers().parseFeed("""<feed xmlns="http://www.w3.org/2005/Atom">
                    <entry><title>Story</title><link href="https://example.org/story"/><summary>Preview only</summary>
                    <updated>${Instant.now().minusSeconds(5)}</updated></entry></feed>""", source)
            }
            val store = settings()
            val reader = ArticleReader(store, download = { _, _, _, _ -> network++
                """{"title":"Story","text":"Downloaded full article", "imageUrl":"https://cdn.example/cover.png"}""".toByteArray()
            })
            val repo = NewsRepository(db, collector, store, articleReader = reader, onFeedUpdated = { scheduled++ })
            assertEquals(1, repo.refresh().added)
            assertEquals(1, scheduled)
            assertEquals(0, network)
            assertEquals("", repo.lookup("https://example.org/story")!!.content)
            val images = Images()
            val result = ArticlePrefetcher(repo, store, images).prefetch()
            assertEquals(1, result.articles)
            assertEquals(1, result.images)
            assertEquals(0, result.failures)
            val saved = repo.lookup("https://example.org/story")!!
            assertTrue(saved.readerLoaded)
            assertEquals("Downloaded full article", repo.readArticle(saved).text)
            assertEquals(1, network)
            assertTrue(images.contains(saved.imageUrl))
        } finally { db.close() }
    }
    @Test fun prefetchNeverReplacesLongerAtomContentOrFeedMetadataWithShorterExtraction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val id = source(db)
            val post = article(id, content = "Full Atom content. ".repeat(100)).copy(author = "Feed author",
                imageUrl = "https://cdn.example/cover.png", imagesJson = "[\"https://cdn.example/cover.png\"]")
            db.articleDao().insertAll(listOf(post))
            val store = settings()
            val reader = ArticleReader(store, download = { _, _, _, _ -> """{"title":"Story","text":"Short extraction"}""".toByteArray() })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            ArticlePrefetcher(repo, store, Images()).prefetch()
            val saved = repo.lookup(post.link)!!
            assertEquals(post.content, saved.content)
            assertEquals(post.author, saved.author)
            assertEquals(ArticleReader.decodeImages(post.imagesJson), ArticleReader.decodeImages(saved.imagesJson))
            assertEquals(post.imageUrl, saved.imageUrl)
            assertTrue(saved.readerLoaded)
        } finally { db.close() }
    }
    @Test fun telegramPublisherIsPrefetchedThroughProxyWithoutReplacingPostOrDuplicatingFeed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val id = source(db, "Telegram")
            val url = "https://devby.io/news/story"
            val post = article(id, "https://t.me/channel/1", "Complete Telegram post").copy(
                linksJson = ArticleLinks.encode(listOf(ArticleLink("Подробнее", url))))
            db.articleDao().insertAll(listOf(post))
            val store = settings()
            var proxies = 0
            val reader = ArticleReader(store, proxyDownload = { target, type, _, _ ->
                assertEquals(url, target); assertEquals("fetch", type); proxies++
                """{"title":"Publisher","text":"Full publisher article"}""".toByteArray()
            }, download = { _, _, _, _ -> error("Direct request forbidden") })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            assertEquals(1, ArticlePrefetcher(repo, store, Images()).prefetch().articles)
            assertEquals(post.content, repo.lookup(post.link)!!.content)
            val linked = repo.lookup(url)!!
            assertTrue(linked.proxyRequired)
            assertEquals("Full publisher article", repo.readArticle(linked).text)
            assertEquals(1, proxies)
            assertEquals(listOf(post.link), repo.observeLatest().first().map { it.link })
        } finally { db.close() }
    }
    @Test fun failedProxyDoesNotEscapeOrPreventOtherSourcesBeingPrefetched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val first = source(db, "Telegram")
            val second = source(db, "Atom")
            val post = article(first, "https://t.me/channel/1", "Saved post").copy(
                linksJson = ArticleLinks.encode(listOf(ArticleLink("Read", "https://devby.io/news/story"))))
            val normal = article(second)
            db.articleDao().insertAll(listOf(post, normal))
            val store = settings()
            var direct = 0
            val reader = ArticleReader(store, proxyDownload = { _, _, _, _ -> error("Worker failure") }, download = { url, _, _, _ ->
                assertEquals(normal.link, url); direct++; """{"title":"Story","text":"Full normal article"}""".toByteArray()
            })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            val result = ArticlePrefetcher(repo, store, Images()).prefetch()
            assertEquals(1, result.articles); assertEquals(1, result.failures); assertEquals(1, direct)
            assertEquals("Saved post", repo.lookup(post.link)!!.content)
            assertNull(repo.lookup("https://devby.io/news/story"))
        } finally { db.close() }
    }
    @Test fun alreadyCachedTelegramTargetRetainsItsBodyAndGainsProxyRequirement() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val id = source(db)
            val saved = article(id, "https://devby.io/news/story", "Existing cache").copy(readerLoaded = true)
            val post = article(id, "https://t.me/channel/1", "Telegram post").copy(
                linksJson = ArticleLinks.encode(listOf(ArticleLink("Read", saved.link))))
            db.articleDao().insertAll(listOf(saved, post))
            val store = settings()
            val reader = ArticleReader(store, proxyDownload = { _, _, _, _ -> error("Must not fetch") }, download = { _, _, _, _ -> error("Must not fetch") })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            assertEquals(0, ArticlePrefetcher(repo, store, Images()).prefetch().articles)
            assertEquals(saved.content, repo.lookup(saved.link)!!.content)
            assertTrue(repo.lookup(saved.link)!!.proxyRequired)
        } finally { db.close() }
    }
    @Test fun batchesAreBoundedAndNextBatchSkipsCompletedArticles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val id = source(db)
            db.articleDao().insertAll((1..5).map { article(id, "https://example.org/story/$it") })
            val store = settings()
            val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
            val reader = ArticleReader(store, download = { url, _, _, _ -> calls += url
                """{"title":"Story","text":"Full text"}""".toByteArray()
            })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            val prefetch = ArticlePrefetcher(repo, store, Images())
            assertEquals(2, prefetch.prefetch(2).articles)
            assertEquals(2, prefetch.prefetch(2).articles)
            assertEquals(4, calls.distinct().size)
            assertEquals(1, prefetch.prefetch(2).articles)
            assertEquals(0, prefetch.prefetch(2).articles)
        } finally { db.close() }
    }
    @Test fun disabledSourcesAndOldHistoryAreNotDownloaded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val enabled = source(db)
            val disabled = source(db, "Disabled", false)
            val live = article(enabled)
            val old = article(enabled, "https://example.org/old").copy(publishedAt = System.currentTimeMillis() - 49 * 60 * 60_000L)
            val hidden = article(disabled, "https://example.org/disabled")
            db.articleDao().insertAll(listOf(live, old, hidden))
            val store = settings()
            val reader = ArticleReader(store, download = { url, _, _, _ -> assertEquals(live.link, url)
                """{"title":"Story","text":"Full live text"}""".toByteArray()
            })
            val repo = NewsRepository(db, emptyCollector, store, articleReader = reader)
            assertEquals(1, ArticlePrefetcher(repo, store, Images()).prefetch().articles)
            assertEquals("", repo.lookup(old.link)!!.content)
            assertEquals("", repo.lookup(hidden.link)!!.content)
        } finally { db.close() }
    }
    @Test fun batchSharesSlotsBetweenSources() {
        val first = (1..10).map { ArticlePrefetcher.Target(article(1, "https://example.org/$it"), 1) }
        val others = (2L..3L).map { ArticlePrefetcher.Target(article(it), it) }
        assertEquals(listOf(1L, 2L, 3L), ArticlePrefetcher.fairBatch(first + others, 3).map { it.originSource })
    }
}
