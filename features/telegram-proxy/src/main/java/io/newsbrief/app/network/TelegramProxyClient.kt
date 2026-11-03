package io.newsbrief.app.network

import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.requireLocalized
import io.newsbrief.app.util.LocalizedFailure
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/** Access keys remain encrypted on disk; short-lived bearer tokens are memory-only. */
data class TelegramProxyConfig(val baseUrl: String, val accessKey: String)

class TelegramProxyClient(
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build(),
    private val config: () -> TelegramProxyConfig
) {
    private var tokenConfig: TelegramProxyConfig? = null
    private var token = ""
    private var expiresAt = 0L

    @Synchronized private fun request(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val current = config()
        requireLocalized(current.baseUrl.isNotBlank() && current.accessKey.isNotBlank(), R.string.nb_err_tg_config, "Настрой HTTPS-адрес и ключ Telegram-прокси в настройках")
        validateBaseUrl(current.baseUrl)
        if (tokenConfig != current || System.currentTimeMillis() >= expiresAt) authenticate(current)
        fun execute(): Pair<Int, String> {
            val url = (current.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder().apply {
                query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            return exchange(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
        }
        var response = execute()
        if (response.first == 401) { authenticate(current); response = execute() }
        requireLocalized(response.first in 200..299, R.string.nb_err_tg_http, "Telegram-прокси: HTTP ${response.first}", response.first)
        return JSONObject(response.second)
    }

    private fun authenticate(current: TelegramProxyConfig) {
        token = ""; expiresAt = 0; tokenConfig = null
        val body = JSONObject().put("accessKey", current.accessKey).toString()
            .toRequestBody("application/json".toMediaType())
        val response = exchange(Request.Builder().url(current.baseUrl.trimEnd('/') + "/auth").post(body).build())
        requireLocalized(response.first in 200..299, R.string.nb_err_tg_auth_http, "Telegram-прокси: авторизация HTTP ${response.first}", response.first)
        val auth = JSONObject(response.second)
        val next = auth.optString("accessToken")
        requireLocalized(next.isNotBlank() && auth.optString("tokenType").equals("Bearer", true), R.string.nb_err_tg_auth, "Telegram-прокси: неверный ответ авторизации")
        token = next; tokenConfig = current
        expiresAt = System.currentTimeMillis() + (auth.optLong("expiresIn", 3600).coerceIn(1, 3600) * 1000 - 10_000).coerceAtLeast(0)
    }

    private fun exchange(request: Request): Pair<Int, String> {
        // Even injected clients cannot forward a credential through a redirect.
        return http.newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(request).execute().use { response ->
            val body = response.body ?: throw LocalizedFailure(R.string.nb_err_tg_empty, "Telegram-прокси: пустой ответ")
            requireLocalized(body.contentLength() <= 3_000_000, R.string.nb_err_tg_large, "Telegram-прокси: ответ слишком большой")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 3_000_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 3_000_001 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            requireLocalized(bytes.size <= 3_000_000, R.string.nb_err_tg_large, "Telegram-прокси: ответ слишком большой")
            response.code to bytes.toString(Charsets.UTF_8)
        }
    }

    fun health(): Boolean = request("/health").optString("status") == "ok"
    fun channels(): List<SourceEntity> {
        val rows = request("/channels").getJSONArray("channels")
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            if (row.optInt("enabled") != 1) return@mapNotNull null
            val key = row.optString("username").takeUnless { it.isBlank() || it == "null" } ?: row.getString("id")
            SourceEntity(name = row.optString("title").ifBlank { key }, kind = SourceKind.TELEGRAM_PROXY,
                address = "telegram-proxy:${channelKey(key)}")
        }
    }
    fun posts(source: SourceEntity): List<ArticleEntity> = postsSince(source.copy(fetchCursor = "")).articles

    fun postsSince(source: SourceEntity): SourceBatch {
        val checkpoint = source.fetchCursor.takeIf { it.isNotBlank() }?.toLongOrNull()
        require(source.fetchCursor.isBlank() || checkpoint != null) { "Invalid Telegram proxy checkpoint" }
        val channel = request("/channel/${channelKey(source.address)}")
        requireLocalized(channel.optInt("enabled") == 1, R.string.nb_err_tg_not_enabled, "Канал ещё не собирается Telegram-прокси")
        val channelId = channel.getString("id")
        var pageCursor: String? = null
        var newest: Long? = null
        val visited = mutableSetOf<String>()
        val posts = linkedMapOf<String, ArticleEntity>()
        repeat(100) {
            val query = mutableMapOf("channel" to channelId, "top" to "100")
            pageCursor?.let { query["cursor"] = it }
            val page = request("/posts", query)
            val rows = page.getJSONArray("posts")
            val ids = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val parts = row.getString("id").split(':')
                require(parts.size == 2 && parts[0] == channelId) { "Invalid Telegram proxy post ID" }
                val id = parts[1].toLongOrNull() ?: error("Invalid Telegram proxy message ID")
                parsePost(row, source, config().baseUrl)?.let { posts.putIfAbsent(it.link, it) }
                id
            }
            if (ids.isEmpty()) {
                if (checkpoint == null && pageCursor == null) return SourceBatch(emptyList())
                return SourceBatch(posts.values.toList(), historyGap = true)
            }
            if (newest == null) newest = ids.max()
            if (checkpoint == null || ids.min() <= checkpoint) {
                return SourceBatch(posts.values.toList(), maxOf(checkpoint ?: 0L, newest!!).toString())
            }
            val next = page.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
                ?: return SourceBatch(posts.values.toList(), historyGap = true)
            require(next.matches(Regex("[0-9]{1,20}:[0-9]{1,20}")) && visited.add(next)) {
                "Invalid or repeated Telegram proxy page cursor"
            }
            pageCursor = next
        }
        return SourceBatch(posts.values.toList(), historyGap = true)
    }

    fun post(article: ArticleEntity): ReaderArticle {
        val uri = URI(article.link)
        val id = if (ArticleLinks.telegramPreview(article.link) != null) {
            val segments = uri.path.removePrefix("/s/").trim('/').split('/')
            val channel = request("/channel/${channelKey(segments[0])}")
            "${channel.getString("id")}:${segments[1]}"
        } else {
            val base = validateBaseUrl(config().baseUrl)
            requireLocalized(uri.scheme == base.scheme && uri.authority == base.authority && uri.path.startsWith(base.path.trimEnd('/') + "/posts/"), R.string.nb_err_tg_wrong_post, "Публикация не принадлежит Telegram-прокси")
            uri.path.substringAfterLast('/')
        }
        requireLocalized(id.matches(Regex("[0-9]{1,20}:[0-9]{1,20}")), R.string.nb_err_tg_post_id, "Неверный ID публикации")
        val source = SourceEntity(id = article.sourceId, name = article.sourceName, kind = SourceKind.TELEGRAM_PROXY, address = "")
        val post = parsePost(request("/posts/$id"), source, config().baseUrl) ?: throw LocalizedFailure(R.string.nb_err_tg_post_text, "Текст публикации недоступен")
        return ReaderArticle(post.title, post.content, post.imageUrl, article.link,
            publishedAt = post.publishedAt, links = ArticleLinks.decode(post.linksJson))
    }

    companion object {
        fun validateBaseUrl(value: String): URI = ProxyUrls.telegramService(value)
        fun channelKey(input: String): String {
            val raw = input.trim().removePrefix("telegram-proxy:").removePrefix("@")
            val key = if (raw.startsWith("https://")) {
                FeedRules.canonicalAddress(raw, SourceKind.TELEGRAM).substringAfterLast('/')
            } else raw
            requireLocalized(key.matches(Regex("(?:[0-9]{1,20}|[A-Za-z][A-Za-z0-9_]{4,31})")), R.string.nb_err_tg_channel_id, "Укажи публичное имя или числовой ID канала")
            return key.lowercase(java.util.Locale.ROOT)
        }
        fun parsePost(row: JSONObject, source: SourceEntity, baseUrl: String): ArticleEntity? {
            val text = row.optString("text").take(60_000)
            val id = row.getString("id")
            requireLocalized(id.matches(Regex("[0-9]{1,20}:[0-9]{1,20}")), R.string.nb_err_tg_post_id, "Неверный ID публикации")
            val links = mutableListOf<ArticleLink>()
            val entities = row.optJSONArray("entities")
            for (index in 0 until (entities?.length() ?: 0)) {
                val entity = entities!!.optJSONObject(index) ?: continue
                val start = entity.optInt("offset", -1); val length = entity.optInt("length", -1)
                // Telegram uses UTF-16 code units, matching Kotlin String indices.
                if (start < 0 || length <= 0 || start.toLong() + length > text.length) continue
                val label = text.substring(start, start + length)
                val type = entity.optString("className").ifBlank { entity.optString("_") }
                val url = when (type) {
                    "MessageEntityTextUrl" -> entity.optString("url")
                    "MessageEntityUrl" -> label
                    else -> continue
                }
                if (ArticleLinks.valid(url)) links += ArticleLink(label, url)
            }
            val sourceUrl = row.optString("sourceUrl")
            val link = sourceUrl.takeIf { ArticleLinks.telegramPreview(it) != null } ?: "${baseUrl.trimEnd('/')}/posts/$id"
            val date = row.getLong("date")
            requireLocalized(date in 0..Long.MAX_VALUE / 1000, R.string.nb_err_tg_date, "Неверная дата публикации")
            // The API currently exposes mediaType only; never invent downloadable image URLs.
            return ArticleEntity(sourceId = source.id, sourceName = source.name, sourcePluginId = SourceKind.TELEGRAM_PROXY,
                title = if (text.isBlank()) source.name else FeedRules.headline(text), excerpt = text.take(1600), content = text,
                readerLoaded = true, proxyRequired = true, linksJson = ArticleLinks.encode(links), link = link, publishedAt = date * 1000)
        }
    }
}
