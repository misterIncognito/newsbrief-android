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

    @Test fun authenticatesReadsChannelsAndUsesNumericChannelFilter() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when (request.url.encodedPath) {
                "/api/auth" -> { assertNull(request.header("Authorization")); assertEquals("POST", request.method)
                    val body = okio.Buffer(); request.body!!.writeTo(body)
                    assertEquals("test-access-key", JSONObject(body.readUtf8()).getString("accessKey"))
                    auth() }
                "/api/channels" -> 200 to """{"channels":[{"id":"12345","title":"Tech","username":"channel","enabled":1},{"id":"9","title":"Off","enabled":0}]}"""
                "/api/channel/channel" -> 200 to """{"id":"12345","title":"Tech","username":"channel","enabled":1}"""
                "/api/posts" -> { assertEquals("12345", request.url.queryParameter("channel")); assertEquals("100", request.url.queryParameter("top")); 200 to JSONObject().put("posts", JSONArray().put(post())).toString() }
                else -> error("Unexpected endpoint")
            }
        }
        val plugin = TelegramProxySourcePlugin(client)
        assertEquals("telegram-proxy:channel", plugin.canonicalAddress("https://t.me/s/Channel"))
        assertEquals("telegram-proxy:12345", plugin.canonicalAddress("12345"))
        assertEquals(source.address, plugin.discover().single().address)
        val article = plugin.collect(source).single()
        assertEquals(SourceKind.TELEGRAM_PROXY, article.sourcePluginId)
        assertEquals(1791456000000L, article.publishedAt)
        assertTrue(article.proxyRequired); assertTrue(article.readerLoaded)
        assertEquals("", article.imageUrl) // mediaType is not an image URL
        assertEquals(listOf("details", "https://example.org/story"), ArticleLinks.decode(article.linksJson).map { it.label })
        assertEquals(1, requests.count { it.url.encodedPath == "/api/auth" })
        requests.drop(1).forEach { assertEquals("Bearer test-token", it.header("Authorization")); assertFalse(it.url.toString().contains("test-access-key")) }
    }
    @Test fun tokenExpiredOnceReauthenticatesAndRetriesOnlyOnce() {
        var auths = 0; var healths = 0
        val client = client { request ->
            if (request.url.encodedPath.endsWith("/auth")) { auths++; auth("token-$auths") }
            else { healths++; if (healths == 1) 401 to "{}" else 200 to """{"status":"ok"}""" }
        }
        assertTrue(client.health()); assertEquals(2, auths); assertEquals(2, healths)
        var attempts = 0
        val denied = client { request -> if (request.url.encodedPath.endsWith("/auth")) auth() else { attempts++; 401 to "{}" } }
        assertTrue(runCatching { denied.health() }.isFailure); assertEquals(2, attempts)
    }
    @Test fun changingServiceOrAccessKeyDiscardsTheOldBearerToken() {
        var configuration = TelegramProxyConfig("https://first.example", "first-key")
        val requests = mutableListOf<Pair<String, String?>>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.host to request.header("Authorization")
            val response = if (request.url.encodedPath == "/auth") {
                val body = okio.Buffer(); request.body!!.writeTo(body)
                assertEquals(configuration.accessKey, JSONObject(body.readUtf8()).getString("accessKey"))
                auth("token-${configuration.accessKey}")
            } else 200 to """{"status":"ok"}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(response.first).message("Test").body(response.second.toResponseBody()).build()
        }.build()
        val client = TelegramProxyClient(http) { configuration }
        assertTrue(client.health())
        configuration = TelegramProxyConfig("https://second.example", "second-key")
        assertTrue(client.health())
        configuration = configuration.copy(accessKey = "rotated-key")
        assertTrue(client.health())
        assertEquals(listOf("first.example" to null, "first.example" to "Bearer token-first-key",
            "second.example" to null, "second.example" to "Bearer token-second-key",
            "second.example" to null, "second.example" to "Bearer token-rotated-key"), requests)
    }
    @Test fun redirectAndPermissionFailuresAreTerminalAndDoNotLeakCredentials() {
        for (status in listOf(302, 403, 502)) {
            val calls = mutableListOf<String>()
            val client = client { request -> calls += request.url.host; if (request.url.encodedPath.endsWith("/auth")) auth() else status to "{}" }
            assertTrue(runCatching { client.health() }.isFailure)
            assertEquals(listOf("telegram.example", "telegram.example"), calls)
        }
    }
    @Test fun badCredentialsAndOversizedResponsesAreRejected() {
        var calls = 0
        val denied = client { calls++; 401 to "{}" }
        assertTrue(runCatching { denied.health() }.isFailure); assertEquals(1, calls)
        val large = client { if (it.url.encodedPath.endsWith("/auth")) auth() else 200 to "x".repeat(3_000_001) }
        assertTrue(runCatching { large.health() }.exceptionOrNull()!!.message!!.contains("слишком большой"))
    }
    @Test fun validatesConfigurationAndChannelWithoutAcceptingOtherHosts() {
        listOf("http://example.org", "https://user:pass@example.org", "https://example.org?q=x", "https://example.org/#secret").forEach {
            assertTrue(runCatching { TelegramProxyClient.validateBaseUrl(it) }.isFailure)
        }
        listOf("https://evil.example/channel", "../../auth", "@bad", "channel?key=x").forEach {
            assertTrue(runCatching { TelegramProxyClient.channelKey(it) }.isFailure)
        }
        assertEquals("telegram.example", TelegramProxyClient.validateBaseUrl("https://telegram.example/api").host)
    }
    @Test fun nullPublicUrlNumericChannelRemainsNativeAndLinksAreSafe() {
        val raw = post().put("sourceUrl", JSONObject.NULL)
        raw.getJSONArray("entities").put(JSONObject().put("className", "MessageEntityTextUrl").put("offset", 0).put("length", 2).put("url", "javascript:bad"))
            .put(JSONObject().put("className", "MessageEntityUrl").put("offset", Int.MAX_VALUE).put("length", 99))
        val article = TelegramProxyClient.parsePost(raw, source, "https://telegram.example/api")!!
        assertEquals("https://telegram.example/api/posts/12345:7", article.link)
        assertTrue(ArticleLinks.isTelegramPost(article)); assertEquals(2, ArticleLinks.decode(article.linksJson).size)
    }
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
        val reader = ArticleReader(SettingsStore(app), plugins = ArticleFetchRegistry.builtIns(client),
            download = { _, _, _, _ -> error("Public network forbidden") }, proxyDownload = { _, _, _, _ -> error("Worker forbidden for proxy post") })
        assertEquals(article.content, reader.load(article).text); assertTrue(requests.isEmpty())
        assertEquals(article.content, reader.load(article, refresh = true).text)
        assertEquals(listOf("/api/auth", "/api/channel/channel", "/api/posts/12345:7"), requests)
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val repo = NewsRepository(db, SourcePluginRegistry.builtIns(SettingsStore(app), client), SettingsStore(app), articleReader = reader)
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
            val registry = SourcePluginRegistry.builtIns(settings, client)
            val repo = NewsRepository(db, registry, settings)
            assertEquals(1, repo.refresh().added)
            val original = repo.observeLatest().first().single()
            db.articleDao().updateArticle(original.copy(imageUrl = "https://cdn.example/cover.jpg"))
            repo.toggleSaved(original)
            text = "Edited full post"
            assertEquals(0, repo.refresh().added)
            val edited = repo.observeSaved().first().single()
            assertEquals(text, edited.content); assertEquals(original.importedAt, edited.importedAt)
            assertEquals("https://cdn.example/cover.jpg", edited.imageUrl)
        } finally { db.close() }
    }
}
