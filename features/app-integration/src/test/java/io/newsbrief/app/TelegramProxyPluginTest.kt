package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TelegramProxyPluginTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val source = SourceEntity(id = 1, name = "Tech", address = "telegram-proxy:channel", kind = SourceKind.TELEGRAM_PROXY)
    private fun post(text: String = "😀 Full post: details https://example.org/story") = JSONObject()
        .put("id", "12345:7").put("channelId", "12345").put("messageId", 7).put("date", 1791456000L)
        .put("text", text).put("sourceUrl", "https://t.me/channel/7").put("mediaType", "MessageMediaPhoto")
        .put("entities", JSONArray().put(JSONObject().put("className", "MessageEntityTextUrl").put("offset", 14).put("length", 7).put("url", "https://publisher.example/full"))
            .put(JSONObject().put("className", "MessageEntityUrl").put("offset", 22).put("length", 25)))
    private fun client(handler: (Request) -> Pair<Int, String>) = TelegramProxyClient(OkHttpClient.Builder().addInterceptor { chain ->
        val result = handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(result.first).message("Test")
            .body(result.second.toResponseBody()).apply { if (result.first == 302) header("Location", "https://forbidden.example/leak") }.build()
    }.build()) { TelegramProxyConfig("https://telegram.example/api", "test-access-key") }
    private fun auth(token: String = "test-token") = 200 to """{"accessToken":"$token","tokenType":"Bearer","expiresIn":3600}"""

    @Test fun proxyPostReaderUsesServiceAndRetainsPublisherProxyPolicyOffline() = runBlocking {
        val requests = mutableListOf<String>()
        val client = client { request ->
            requests += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/auth" -> auth()
                "/api/channel/channel" -> 200 to """{"id":"12345"}"""
                "/api/posts/12345:7" -> 200 to post().toString()
                else -> error("Unexpected request")
            }
        }
        val article = TelegramProxyClient.parsePost(post(), source, "https://telegram.example/api")!!
        val reader = ArticleReader(SettingsStore(app), plugins = ArticleFetchRegistry.builtIns(TelegramProxyContent(client)),
            download = { _, _, _, _ -> error("Public network forbidden") }, proxyDownload = { _, _, _, _ -> error("Worker forbidden for proxy post") })
        assertEquals(article.content, reader.load(article).text); assertTrue(requests.isEmpty())
        assertEquals(article.content, reader.load(article, refresh = true).text)
        assertEquals(listOf("/api/auth", "/api/channel/channel", "/api/posts/12345:7"), requests)
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val repo = TestNewsRepository(db, SourcePluginRegistry.builtIns(SettingsStore(app), listOf(TelegramProxySourcePlugin(client))), SettingsStore(app), articleReader = reader)
            repo.toggleSaved(article)
            repo.removeSource(source.id)
            val saved = repo.observeSaved().first().single()
            assertEquals(SourceKind.TELEGRAM_PROXY, saved.sourcePluginId)
            assertEquals(article.content, repo.readArticle(saved).text)
            val linked = repo.linkedArticle(saved, "https://publisher.example/full")
            assertTrue(linked.proxyRequired); assertEquals("", linked.sourcePluginId)
        } finally { db.close() }
    }
    @Test fun editedPostsMergeWithoutLosingBookmarksOrCachedImages() = runBlocking {
        var text = "Original post"
        val client = client { request -> when (request.url.encodedPath) {
            "/api/auth" -> auth()
            "/api/channel/channel" -> 200 to """{"id":"12345","enabled":1}"""
            "/api/posts" -> 200 to JSONObject().put("posts", JSONArray().put(post(text))).toString()
            else -> error("Unexpected endpoint")
        } }
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val settings = SettingsStore(app)
            settings.markSourcesCatalogV2Seeded(); settings.markSourcesCatalogV3Seeded(); settings.markSourcesCatalogV4Seeded(); settings.markSourcesCatalogV5Seeded()
            db.sourceDao().insert(source)
            val registry = SourcePluginRegistry.builtIns(settings, listOf(TelegramProxySourcePlugin(client)))
            var now = 1_000_000L
            val repo = TestNewsRepository(db, registry, settings, clock = { now })
            assertEquals(1, repo.refresh().added)
            val original = repo.observeLatest().first().single()
            db.articleDao().updateArticle(original.copy(imageUrl = "https://cdn.example/cover.jpg"))
            repo.toggleSaved(original)
            text = "Edited full post"
            now += TestNewsRepository.SYNC_COOLDOWN_MILLIS
            assertEquals(0, repo.refresh().added)
            val edited = repo.observeSaved().first().single()
            assertEquals(text, edited.content); assertEquals(original.importedAt, edited.importedAt)
            assertEquals("https://cdn.example/cover.jpg", edited.imageUrl)
        } finally { db.close() }
    }

}
