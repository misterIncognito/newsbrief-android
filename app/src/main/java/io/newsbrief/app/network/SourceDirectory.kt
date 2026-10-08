package io.newsbrief.app.network

import io.newsbrief.app.data.FeedCategory
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.data.SourceCatalog
import io.newsbrief.app.util.FeedRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

/** Search indexes, rather than a locally invented list of thousands of sources. */
enum class DirectoryProvider(val label: String) { RSS("RSS / сайты"), TELEGRAM("Telegram"), CURATED("Рекомендации") }
data class DirectorySource(
    val title: String, val address: String, val kind: String, val description: String = "",
    val language: String = "", val audience: Long? = null, val categories: Set<FeedCategory> = emptySet(),
    val topics: Set<String> = emptySet()
)
data class DirectoryResults(
    val items: List<DirectorySource>, val total: Int? = null, val directorySize: Int? = null,
    val cached: Boolean = false, val notice: String? = null,
    val category: String? = null, val nextOffset: Int? = null
)
interface DirectoryNetwork {
    suspend fun search(provider: DirectoryProvider, query: String): DirectoryResults
    suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults =
        error("У этого поиска нет следующих страниц")
}

/** Topic shortcuts browse categories; specific names use the provider's working search tool. */
object TelegramTopics {
    fun category(query: String): String? = when (query.trim().lowercase(java.util.Locale.ROOT)) {
        "ai", "ии", "artificial intelligence", "искусственный интеллект", "нейросети" -> "ai"
        "tech", "technology", "technologies", "технологии" -> "tech"
        "news", "новости" -> "news"
        "business", "бизнес" -> "business"
        "programming", "coding", "программирование", "код" -> "dev"
        else -> null
    }
    fun show(source: DirectorySource, hidePromotions: Boolean): Boolean =
        !hidePromotions || source.topics.none { it == "crypto" || it == "gaming" }
    fun merge(previous: DirectoryResults, next: DirectoryResults): DirectoryResults {
        require(previous.category != null && previous.category == next.category) { "Категория изменилась" }
        require(next.nextOffset == null || next.nextOffset > (previous.nextOffset ?: -1)) { "Каталог повторил страницу" }
        return next.copy(items = (previous.items + next.items).distinctBy { it.address.lowercase(java.util.Locale.ROOT) },
            cached = previous.cached || next.cached)
    }
}

class PublicDirectoryNetwork : DirectoryNetwork {
    private val client = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun search(provider: DirectoryProvider, query: String): DirectoryResults = withContext(Dispatchers.IO) {
        when (provider) {
            DirectoryProvider.RSS -> DirectoryParser.feedly(request(Request.Builder().url(feedlyUrl(query)).build()))
            DirectoryProvider.TELEGRAM -> {
                val category = TelegramTopics.category(query)
                if (category != null) catalog(category, 0)
                else DirectoryParser.tgden(rpc("search_telegram", JSONObject()
                    .put("query", query.removePrefix("@")).put("type", "channel").put("limit", 25)))
            }
            DirectoryProvider.CURATED -> DirectoryResults(SourceCatalog.search(query).map {
                DirectorySource(it.name, it.address, it.kind, it.category)
            })
        }
    }
    override suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults =
        withContext(Dispatchers.IO) {
            require(provider == DirectoryProvider.TELEGRAM)
            val category = TelegramTopics.category(query) ?: error("Уточни запрос, чтобы найти другие каналы")
            catalog(category, offset)
        }
    private fun catalog(category: String, offset: Int): DirectoryResults =
        DirectoryParser.tgden(request(Request.Builder().url(catalogUrl(category, offset)).build()), category, offset)
    private fun rpc(name: String, arguments: JSONObject): String {
        require(name == "search_telegram")
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
            .put("params", JSONObject().put("name", name).put("arguments", arguments)).toString()
        return request(Request.Builder().url("https://tgden.com/api/mcp")
            .header("Accept", "application/json, text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType())).build())
    }
    private fun request(request: Request): String = client.newCall(request.newBuilder()
        .header("User-Agent", "NewsBrief/0.4.5").build()).execute().use { response ->
        check(response.isSuccessful) { "Каталог недоступен (HTTP ${response.code})" }
        val body = response.body ?: error("Пустой ответ каталога")
        require(body.contentLength() <= 2_000_000) { "Слишком большой ответ каталога" }
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var size = input.read(buffer)
            while (size != -1) {
                require(output.size() + size <= 2_000_000) { "Слишком большой ответ каталога" }
                output.write(buffer, 0, size)
                size = input.read(buffer)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }
    companion object {
        fun catalogUrl(category: String, offset: Int): String {
            require(category in setOf("ai", "tech", "news", "business", "dev"))
            require(offset >= 0)
            return "https://tgden.com/api/catalog".toHttpUrl().newBuilder()
                .addQueryParameter("category", category).addQueryParameter("limit", "25")
                .addQueryParameter("offset", offset.toString()).build().toString()
        }
        fun feedlyUrl(query: String): String = "https://cloud.feedly.com/v3/search/feeds".toHttpUrl()
            .newBuilder().addQueryParameter("query", query).addQueryParameter("count", "100").build().toString()
    }
}

object DirectoryParser {
    private fun text(value: String, limit: Int) = Jsoup.parse(value).text().take(limit)
    fun categories(tags: List<String>): Set<FeedCategory> = buildSet {
        val terms = tags.joinToString(" ").lowercase(java.util.Locale.ROOT)
        if (Regex("tech|software|programming|developer|cybersecurity|artificial.?intelligence|технолог|программ|\\bai\\b|\\bit\\b").containsMatchIn(terms)) add(FeedCategory.TECH)
        if (Regex("business|finance|econom|startup|бизнес|финанс").containsMatchIn(terms)) add(FeedCategory.BUSINESS)
        if (Regex("belarus|беларус").containsMatchIn(terms)) add(FeedCategory.BELARUS)
        if (Regex("poland|warsaw|польш|варшав").containsMatchIn(terms)) add(FeedCategory.POLAND)
        if (Regex("world|international|миров").containsMatchIn(terms)) add(FeedCategory.WORLD)
        if (isEmpty() && Regex("news|новост").containsMatchIn(terms)) add(FeedCategory.GENERAL)
    }
    fun feedly(raw: String): DirectoryResults {
        val root = JSONObject(raw)
        check(root.optBoolean("success", true)) { "Ошибка поиска RSS" }
        val array = root.optJSONArray("results") ?: error("Некорректный ответ RSS-каталога")
        val items = (0 until minOf(array.length(), 100)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            if (item.has("valid") && !item.optBoolean("valid")) return@mapNotNull null
            var url = item.optString("feedId").ifBlank { item.optString("id") }.removePrefix("feed/")
            if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://")
            val address = runCatching {
                val uri = URI(url)
                val host = uri.host?.lowercase(java.util.Locale.ROOT) ?: error("No host")
                require(host.contains('.') && !host.endsWith(".local") && host != "localhost" &&
                    !host.matches(Regex("[0-9.]+")) && uri.port in setOf(-1, 443))
                FeedRules.canonicalAddress(url, SourceKind.RSS)
            }.getOrNull() ?: return@mapNotNull null
            val tags = item.optJSONArray("topics")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            DirectorySource(text(item.optString("title").ifBlank { URI(address).host }, 240), address,
                SourceKind.RSS, text(item.optString("description"), 500), item.optString("language"),
                item.optLong("subscribers", -1).takeIf { it >= 0 }, categories(tags))
        }.distinctBy { it.address }
        return DirectoryResults(items)
    }
    fun rpcPayload(raw: String): JSONObject {
        val root = JSONObject(raw)
        check(!root.has("error")) { "Ошибка Telegram-каталога" }
        val result = root.optJSONObject("result") ?: error("Некорректный ответ Telegram-каталога")
        check(!result.optBoolean("isError")) { "Ошибка поиска Telegram" }
        result.optJSONObject("structuredContent")?.let { return it }
        val content = result.optJSONArray("content") ?: error("Нет результатов каталога")
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") return JSONObject(block.getString("text"))
        }
        error("Нет данных каталога")
    }
    /** Legacy TGX response contract, retained for existing integrations and saved data. */
    fun telegram(raw: String): DirectoryResults {
        val root = rpcPayload(raw)
        val array = root.optJSONArray("results") ?: error("Нет списка Telegram-каналов")
        val items = (0 until minOf(array.length(), 20)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("type") != "channel") return@mapNotNull null
            val handle = item.optString("handle").removePrefix("@")
            val address = runCatching { FeedRules.canonicalAddress("@$handle", SourceKind.TELEGRAM) }.getOrNull()
                ?: return@mapNotNull null
            val tags = item.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            DirectorySource(text(item.optString("name").ifBlank { handle }, 240), address, SourceKind.TELEGRAM,
                text(item.optString("summary"), 500), item.optString("language"),
                item.optLong("memberCount", -1).takeIf { it >= 0 }, categories(tags))
        }.distinctBy { it.address.lowercase(java.util.Locale.ROOT) }
        return DirectoryResults(items, root.optInt("total", -1).takeIf { it >= 0 })
    }
    fun tgden(raw: String, category: String? = null, offset: Int = 0): DirectoryResults {
        val response = JSONObject(raw)
        val root = if (response.has("result") || response.has("error")) rpcPayload(raw) else response
        check(!root.optBoolean("isDemo")) { "Каталог вернул демонстрационные данные вместо результатов" }
        val array = root.optJSONArray("items") ?: error("Нет списка Telegram-каналов")
        val items = (0 until minOf(array.length(), 25)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("entity_type", "channel") != "channel" || item.optBoolean("is_private")) return@mapNotNull null
            val handle = item.optString("username").removePrefix("@")
            val address = runCatching { FeedRules.canonicalAddress("@$handle", SourceKind.TELEGRAM) }.getOrNull()
                ?: return@mapNotNull null
            val tags = item.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val languages = item.optJSONArray("language")?.let { a -> (0 until a.length()).map { a.optString(it) }.joinToString(" / ") }.orEmpty()
            DirectorySource(text(item.optString("title").ifBlank { handle }, 240), address, SourceKind.TELEGRAM,
                if (item.isNull("description_ai_summary")) "" else text(item.optString("description_ai_summary"), 500),
                languages, item.optLong("subscribers_count", -1).takeIf { it >= 0 }, categories(tags),
                tags.map { it.lowercase(java.util.Locale.ROOT) }.toSet())
        }.distinctBy { it.address.lowercase(java.util.Locale.ROOT) }
        val total = root.optInt("total", -1).takeIf { it >= 0 }
        // Advance by raw records, including private/unusable entries; never repeat page 1.
        val next = if (category != null && array.length() in 1..25 && total != null && offset + array.length() < total)
            offset + array.length() else null
        return DirectoryResults(items, total, category = category, nextOffset = next)
    }
    fun encode(result: DirectoryResults): String = JSONObject()
        .put("total", result.total).put("directorySize", result.directorySize)
        .put("category", result.category).put("nextOffset", result.nextOffset)
        .put("items", JSONArray(result.items.map { item -> JSONObject()
            .put("title", item.title).put("address", item.address).put("kind", item.kind)
            .put("description", item.description).put("language", item.language).put("audience", item.audience)
            .put("categories", JSONArray(item.categories.map { it.name })).put("topics", JSONArray(item.topics.toList())) })).toString()
    fun decode(raw: String): DirectoryResults {
        val root = JSONObject(raw)
        val array = root.getJSONArray("items")
        return DirectoryResults((0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val tags = item.getJSONArray("categories")
            DirectorySource(item.getString("title"), item.getString("address"), item.getString("kind"),
                item.optString("description"), item.optString("language"),
                if (item.isNull("audience")) null else item.getLong("audience"),
                (0 until tags.length()).mapNotNull { runCatching { FeedCategory.valueOf(tags.getString(it)) }.getOrNull() }.toSet(),
                item.optJSONArray("topics")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty())
        }, if (root.isNull("total")) null else root.getInt("total"),
            if (root.isNull("directorySize")) null else root.getInt("directorySize"),
            category = if (root.isNull("category")) null else root.getString("category"),
            nextOffset = if (root.isNull("nextOffset")) null else root.getInt("nextOffset"))
    }
}
