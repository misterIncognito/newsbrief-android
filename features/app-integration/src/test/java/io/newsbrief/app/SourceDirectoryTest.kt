package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class SourceDirectoryTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val found = DirectorySource("Tech publisher", "https://tech.example/rss", SourceKind.RSS,
        "Developer news", "en", 1234, setOf(FeedCategory.TECH))
    private fun rpc(payload: JSONObject) = JSONObject().put("result", JSONObject().put("content",
        JSONArray().put(JSONObject().put("type", "text").put("text", payload.toString())))).toString()
    private fun channel(handle: String, kind: String = "channel") = JSONObject().put("name", "<b>Tech</b>")
        .put("handle", handle).put("type", kind).put("summary", "<p>Code news</p>")
        .put("categories", JSONArray(listOf("tech"))).put("memberCount", 4567).put("language", "ru")
    private suspend fun withDb(block: suspend (NewsDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try { block(db) } finally { db.close() }
    }
    private fun network(block: suspend (DirectoryProvider, String) -> DirectoryResults) = object : DirectoryNetwork {
        override suspend fun search(provider: DirectoryProvider, query: String) = block(provider, query)
    }
    private fun repository(db: NewsDatabase, collect: suspend (SourceEntity) -> List<ArticleEntity>) =
        TestNewsRepository(db, object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = collect(source)
        }, SettingsStore(app))
    private fun article(source: SourceEntity, content: String = "New feed text") = ArticleEntity(
        sourceId = source.id, sourceName = source.name, title = "Article", excerpt = "Preview",
        content = content, link = "https://tech.example/story", publishedAt = 123)

    @Test fun rssParsesFeedlyContractAndUpgradesHttpToHttps() {
        val raw = """{"success":true,"results":[{"feedId":"feed/http://tech.example/rss","title":"<b>Tech</b>","description":"<p>Code</p>","language":"en","valid":true,"subscribers":42,"topics":["technology","business"]}]}"""
        val source = DirectoryParser.feedly(raw).items.single()
        assertEquals("https://tech.example/rss", source.address)
        assertEquals("Tech", source.title)
        assertEquals("Code", source.description)
        assertEquals(42L, source.audience)
        assertEquals(setOf(FeedCategory.TECH, FeedCategory.BUSINESS), source.categories)
    }
    @Test fun rssRejectsInvalidLocalCredentialsAndNonHttpsLinksAndDeduplicates() {
        val urls = listOf("https://valid.example/rss", "https://valid.example/rss", "ftp://valid.example/rss",
            "https://127.0.0.1/rss", "https://printer.local/rss", "https://user:pass@valid.example/rss", "https://valid.example:8080/rss")
        val entries = JSONArray(urls.map { JSONObject().put("id", "feed/$it") })
            .put(JSONObject().put("id", "feed/https://dead.example/rss").put("valid", false))
            .put("malformed")
        assertEquals(listOf("https://valid.example/rss"), DirectoryParser.feedly(JSONObject().put("results", entries).toString()).items.map { it.address })
    }
    @Test fun rssRequiresAResultsArrayAndRejectsServiceErrors() {
        assertThrows(io.newsbrief.app.util.LocalizedFailure::class.java) { DirectoryParser.feedly("{\"success\":false}") }
        assertThrows(io.newsbrief.app.util.LocalizedFailure::class.java) { DirectoryParser.feedly("{}") }
    }
    @Test fun queryEncodingPreservesPlusAmpersandAndUnicode() {
        val query = "C++ & ИИ / programming"
        assertEquals(query, PublicDirectoryNetwork.feedlyUrl(query).toHttpUrl().queryParameter("query"))
        assertEquals("100", PublicDirectoryNetwork.feedlyUrl(query).toHttpUrl().queryParameter("count"))
    }
    @Test fun telegramParsesNestedRpcPayloadMetadataAndTotal() {
        val result = DirectoryParser.telegram(rpc(JSONObject().put("total", 841).put("results", JSONArray().put(channel("technews")))))
        assertEquals(841, result.total)
        val source = result.items.single()
        assertEquals("https://t.me/s/technews", source.address)
        assertEquals("Tech", source.title)
        assertEquals("Code news", source.description)
        assertEquals(4567L, source.audience)
        assertEquals(setOf(FeedCategory.TECH), source.categories)
    }
    @Test fun telegramSkipsGroupsBotsInvalidHandlesAndDuplicates() {
        val entries = JSONArray().put(channel("technews")).put(channel("TechNews"))
            .put(channel("groupnews", "group")).put(channel("newsbot", "bot"))
            .put(channel("+private")).put(channel(""))
        assertEquals(1, DirectoryParser.telegram(rpc(JSONObject().put("results", entries))).items.size)
    }
    @Test fun telegramRejectsRpcToolErrorsAndMissingResults() {
        assertThrows(io.newsbrief.app.util.LocalizedFailure::class.java) { DirectoryParser.telegram("{\"error\":{\"code\":-1}}") }
        assertThrows(io.newsbrief.app.util.LocalizedFailure::class.java) { DirectoryParser.telegram("{\"result\":{\"isError\":true}}") }
        assertThrows(io.newsbrief.app.util.LocalizedFailure::class.java) { DirectoryParser.telegram(rpc(JSONObject())) }
    }
    @Test fun directoryStatsAndStructuredPayloadAreSupported() {
        assertEquals(17343, DirectoryParser.rpcPayload(rpc(JSONObject().put("publicChannels", 17343))).getInt("publicChannels"))
        val root = JSONObject().put("result", JSONObject().put("structuredContent", JSONObject().put("results", JSONArray().put(channel("technews")))))
        assertEquals(1, DirectoryParser.telegram(root.toString()).items.size)
    }
    @Test fun resultCacheRoundTripsMetadataAndNullableCounts() {
        val result = DirectoryResults(listOf(found, found.copy(address = "https://second.example/rss", audience = null)), 841, 17343)
        assertEquals(result, DirectoryParser.decode(DirectoryParser.encode(result)))
        assertEquals(DirectoryResults(emptyList()), DirectoryParser.decode(DirectoryParser.encode(DirectoryResults(emptyList()))))
    }
    @Test fun freshCacheAvoidsASecondRequestAndNormalizesQuery() = runBlocking { withDb { db ->
        var calls = 0
        val repo = SourceDirectoryRepository(db.catalogSearchDao(), network { _, query ->
            calls++; assertEquals("technology", query); DirectoryResults(listOf(found))
        }, now = { 1000 })
        assertFalse(repo.search(DirectoryProvider.RSS, " technology ").cached)
        assertTrue(repo.search(DirectoryProvider.RSS, "TECHNOLOGY").cached)
        assertEquals(1, calls)
    } }
    @Test fun providerCachesAreSeparateAndStaleDataWorksOffline() = runBlocking { withDb { db ->
        var time = 1000L
        var offline = false
        val repo = SourceDirectoryRepository(db.catalogSearchDao(), network { provider, _ ->
            if (offline) error("Offline")
            DirectoryResults(listOf(found.copy(title = provider.name)))
        }, now = { time })
        repo.search(DirectoryProvider.RSS, "tech")
        repo.search(DirectoryProvider.TELEGRAM, "tech")
        time += 300_001; offline = true
        val saved = repo.search(DirectoryProvider.TELEGRAM, "tech")
        assertEquals("TELEGRAM", saved.items.single().title)
        assertTrue(saved.cached); assertNotNull(saved.notice)
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.search(DirectoryProvider.RSS, "unknown") } }
    } }
    @Test fun cancellationIsNeverMaskedByAnOfflineCacheFallback() = runBlocking { withDb { db ->
        db.catalogSearchDao().save(CatalogSearchEntity("RSS:tech", DirectoryParser.encode(DirectoryResults(listOf(found))), 1))
        val repo = SourceDirectoryRepository(db.catalogSearchDao(), network { _, _ -> throw CancellationException("Search superseded") }, now = { 400_000 })
        assertThrows(CancellationException::class.java) { runBlocking { repo.search(DirectoryProvider.RSS, "tech") } }
    } }
    @Test fun cacheRetentionKeepsOnlyTheLatestFiftySearches() = runBlocking { withDb { db ->
        repeat(51) { db.catalogSearchDao().save(CatalogSearchEntity("RSS:query-$it", "{}", it.toLong())) }
        db.catalogSearchDao().prune()
        assertNull(db.catalogSearchDao().find("RSS:query-0"))
        assertNotNull(db.catalogSearchDao().find("RSS:query-1"))
        assertNotNull(db.catalogSearchDao().find("RSS:query-50"))
    } }
    @Test fun emptyOrUnreachableSourcesAreNotInserted() = runBlocking { withDb { db ->
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(db) { emptyList() }.addDiscoveredSource(found) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository(db) { error("HTTP 403") }.addDiscoveredSource(found) } }
        assertEquals(0, db.sourceDao().count())
        assertTrue(db.articleDao().recent(0, 100).isEmpty())
    } }
    @Test fun addingVerifiedSourceLoadsArticlesImmediatelyAndPersistsTechCategory() = runBlocking { withDb { db ->
        val repo = repository(db) { listOf(article(it)) }
        assertTrue(repo.addDiscoveredSource(found))
        val source = db.sourceDao().enabled().single()
        assertEquals(setOf(FeedCategory.TECH), FeedFilters.categories(source))
        assertEquals(listOf(source.id), FeedFilters.sourceIds(listOf(source), FeedCategory.TECH, null))
        assertEquals(source.id, repo.observeLatest().first().single().sourceId)
        assertFalse(repo.addDiscoveredSource(found))
        assertEquals(1, db.sourceDao().count())
    } }
    @Test fun readdingDiscoveredSourceRestoresOfflineArticleWithoutOverwritingText() = runBlocking { withDb { db ->
        val repo = repository(db) { listOf(article(it)) }
        val id = db.sourceDao().insert(SourceEntity(name = found.title, address = found.address, kind = found.kind))
        db.articleDao().insertAll(listOf(article(db.sourceDao().enabled().single(), "Saved offline reader text")))
        repo.removeSource(id)
        assertTrue(repo.addDiscoveredSource(found))
        assertEquals("Saved offline reader text", repo.observeLatest().first().single().content)
        assertNotEquals(id, repo.observeLatest().first().single().sourceId)
    } }
    @Test fun failedArticleWriteRollsBackTheNewSource() = runBlocking { withDb { db ->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_article BEFORE INSERT ON articles BEGIN SELECT RAISE(ABORT, 'Simulated disk failure'); END")
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) { runBlocking {
            repository(db) { listOf(article(it)) }.addDiscoveredSource(found)
        } }
        assertEquals(0, db.sourceDao().count())
    } }
}
