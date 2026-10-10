package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TelegramCatalogTest {
    private val found = DirectorySource("Tech publisher", "https://tech.example/rss", SourceKind.RSS)
    private fun channel(handle: String) = JSONObject().put("title", "AI News").put("username", handle)
        .put("entity_type", "channel").put("categories", JSONArray(listOf("ai")))
    @Test fun aiShortcutsBrowseTheWholeCategoryButPublisherNamesRemainSearches() {
        listOf("AI", "ИИ", "artificial intelligence", "нейросети", "искусственный интеллект").forEach {
            assertEquals("ai", TelegramTopics.category(it))
        }
        assertEquals("tech", TelegramTopics.category("technology"))
        assertNull(TelegramTopics.category("ChatGPT"))
        assertNull(TelegramTopics.category("@aichannel"))
    }
    @Test fun categoryRequestsUseRealOffsetsRatherThanIgnoredPageParameters() {
        val url = PublicDirectoryNetwork.catalogUrl("ai", 25).toHttpUrl()
        assertEquals("ai", url.queryParameter("category"))
        assertEquals("25", url.queryParameter("offset"))
        assertEquals("25", url.queryParameter("limit"))
        assertNull(url.queryParameter("q"))
        assertThrows(IllegalArgumentException::class.java) { PublicDirectoryNetwork.catalogUrl("ai", -1) }
    }
    @Test fun categoryPageAdvancesPastPrivateAndMissingHandles() {
        val entries = JSONArray().put(channel("technews")).put(channel("hiddennews").put("is_private", true))
            .put(channel("missingname").put("username", JSONObject.NULL))
        val result = DirectoryParser.tgden(JSONObject().put("items", entries).put("total", 9377).toString(), "ai", 25)
        assertEquals(1, result.items.size)
        assertEquals(28, result.nextOffset)
        assertEquals("ai", result.category)
        assertEquals(9377, result.total)
    }
    @Test fun finalAndEmptyPagesStopAndKeywordSearchDoesNotInventPaging() {
        val raw = JSONObject().put("items", JSONArray().put(channel("technews"))).put("total", 26).toString()
        assertNull(DirectoryParser.tgden(raw, "ai", 25).nextOffset)
        assertNull(DirectoryParser.tgden(raw).nextOffset)
        assertNull(DirectoryParser.tgden("{\"items\":[],\"total\":9377}", "ai", 25).nextOffset)
    }
    @Test fun demoResultsAreRejectedAndNullDescriptionsDoNotBecomeLiteralNull() {
        assertThrows(IllegalStateException::class.java) { DirectoryParser.tgden("{\"isDemo\":true,\"items\":[]}") }
        val raw = JSONObject().put("items", JSONArray().put(channel("technews").put("description_ai_summary", JSONObject.NULL))).toString()
        assertEquals("", DirectoryParser.tgden(raw).items.single().description)
    }
    @Test fun aiFilterHidesCryptoAndGamingAndCanBeDisabled() {
        assertFalse(TelegramTopics.show(found.copy(topics = setOf("ai", "crypto")), true))
        assertFalse(TelegramTopics.show(found.copy(topics = setOf("ai", "gaming")), true))
        assertTrue(TelegramTopics.show(found.copy(topics = setOf("ai", "news")), true))
        assertTrue(TelegramTopics.show(found.copy(topics = setOf("ai", "crypto")), false))
    }
    @Test fun mergingPagesDeduplicatesWithoutSummingOverlappingTotals() {
        val first = DirectoryResults(listOf(found), total = 9377, category = "ai", nextOffset = 25)
        val next = DirectoryResults(listOf(found, found.copy(address = "https://second.example/rss")),
            total = 9377, category = "ai", nextOffset = 50)
        val merged = TelegramTopics.merge(first, next)
        assertEquals(2, merged.items.size)
        assertEquals(9377, merged.total)
        assertEquals(50, merged.nextOffset)
        assertThrows(IllegalArgumentException::class.java) { TelegramTopics.merge(first, next.copy(nextOffset = 25)) }
        assertThrows(IllegalArgumentException::class.java) { TelegramTopics.merge(first, next.copy(category = "tech")) }
    }
    @Test fun telegramCacheInvalidatesThePreviousThreeResultProviderAndCachesPagesSeparately() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            db.catalogSearchDao().save(CatalogSearchEntity("TELEGRAM:artificial intelligence",
                DirectoryParser.encode(DirectoryResults(emptyList(), total = 3)), 1000))
            var requests = 0
            val remote = object : DirectoryNetwork {
                override suspend fun search(provider: DirectoryProvider, query: String): DirectoryResults {
                    requests++
                    return DirectoryResults(listOf(found), total = 9377, category = "ai", nextOffset = 25)
                }
                override suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults {
                    requests++; assertEquals(25, offset)
                    return DirectoryResults(listOf(found.copy(title = "Second page")), total = 9377, category = "ai", nextOffset = 50)
                }
            }
            val repo = SourceDirectoryRepository(db.catalogSearchDao(), remote, now = { 1000 })
            assertEquals(9377, repo.search(DirectoryProvider.TELEGRAM, "artificial intelligence").total)
            assertEquals("Second page", repo.search(DirectoryProvider.TELEGRAM, "artificial intelligence", 25).items.single().title)
            assertTrue(repo.search(DirectoryProvider.TELEGRAM, "artificial intelligence", 25).cached)
            assertEquals(2, requests)
        } finally { db.close() }
    }
    @Test fun keywordRpcUsesPublicChannelMetadataAndCategoryTags() {
        val payload = JSONObject().put("total", 333).put("items", JSONArray().put(channel("ainews")
            .put("description_ai_summary", "<p>AI news</p>").put("subscribers_count", 1234)
            .put("language", JSONArray(listOf("ru", "en")))))
        val raw = JSONObject().put("result", JSONObject().put("content", JSONArray().put(
            JSONObject().put("type", "text").put("text", payload.toString())))).toString()
        val result = DirectoryParser.tgden(raw)
        assertEquals(333, result.total)
        assertEquals("AI news", result.items.single().description)
        assertEquals(1234L, result.items.single().audience)
        assertEquals("ru / en", result.items.single().language)
        assertEquals(setOf(FeedCategory.TECH), result.items.single().categories)
        assertEquals(setOf("ai"), result.items.single().topics)
        assertNull(result.nextOffset)
    }

}
