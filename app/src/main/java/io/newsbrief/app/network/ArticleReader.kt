package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Instant

class ArticleReader(private val settings: SettingsStore,
    private val proxyDownload: (String, String, String, Int) -> ByteArray = { url, type, worker, limit ->
        NewsHttp.download(url, type, worker, limit, forceProxy = true)
    },
    val plugins: ArticleFetchRegistry = ArticleFetchRegistry.builtIns(TelegramProxyClient {
        TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
    }),
    private val download: (String, String, String, Int) -> ByteArray = { url, type, worker, limit ->
        NewsHttp.download(url, type, worker, limit)
    }
) {
    suspend fun load(article: ArticleEntity, refresh: Boolean = false): ReaderArticle = withContext(Dispatchers.IO) {
        val cached = ReaderArticle(article.title, article.content.ifBlank { article.excerpt },
            article.imageUrl, article.link, publishedAt = article.publishedAt, author = article.author,
            images = decodeImages(article.imagesJson), links = ArticleLinks.decode(article.linksJson))
        // Full feed content and saved reader text are usable without a network request.
        val telegramPreview = ArticleLinks.telegramPreview(article.link)
        if (!refresh && if (article.sourcePluginId == io.newsbrief.app.data.SourceKind.TELEGRAM_PROXY) article.content.isNotBlank()
            else if (telegramPreview != null) cached.links.isNotEmpty()
            else article.readerLoaded || article.content.isNotBlank()) return@withContext cached
        try {
            val result = plugins.fetch(ArticleFetchRequest(article, cached, settings.workerUrl(), download, proxyDownload))
            require(result.text.isNotBlank()) { "Article text unavailable" }
            result.copy(title = result.title.ifBlank { cached.title },
                imageUrl = result.imageUrl.ifBlank { cached.imageUrl },
                publishedAt = result.publishedAt ?: cached.publishedAt,
                author = result.author.ifBlank { cached.author },
                images = result.images.ifEmpty { cached.images })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            cached.copy(notice = "Не удалось загрузить полную статью: ${e.message.orEmpty().take(90)}")
        }
    }

    companion object {
        fun decodeImages(raw: String): List<String> = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())

        fun htmlText(element: Element): String = FeedMarkup.htmlText(element)

        fun parseArticle(raw: String, original: String): ReaderArticle {
            if (raw.trimStart().startsWith("{")) {
                val root = JSONObject(raw)
                val nested = root.optJSONObject("article") ?: root.optJSONObject("data") ?: root
                fun string(vararg keys: String): String = keys.asSequence()
                    .map { nested.opt(it) }.filterIsInstance<String>()
                    .map { it.trim() }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
                val paragraphs = nested.optJSONArray("paragraphs")
                val text = if (paragraphs != null && paragraphs.length() > 0)
                    (0 until paragraphs.length()).mapNotNull { paragraphs.opt(it) as? String }
                        .joinToString("\n\n")
                else string("text", "content", "body", "articleBody", "contentText", "content_text", "fullText", "markdown", "html", "description", "excerpt")
                val plain = if (Regex("<[A-Za-z][^>]*>").containsMatchIn(text)) htmlText(Jsoup.parse(text)) else text.trim()
                val images = mutableListOf<String>()
                val lead = string("imageUrl", "image_url", "image", "leadImage", "lead_image_url", "thumbnail")
                    .ifBlank { nested.optJSONObject("image")?.optString("url").orEmpty() }
                if (lead.startsWith("https://")) images += lead
                nested.optJSONArray("images")?.let { array ->
                    for (i in 0 until array.length()) {
                        val image = (array.opt(i) as? String) ?: array.optJSONObject(i)?.optString("url").orEmpty()
                        if (image.startsWith("https://")) images += image
                    }
                }
                // Keep original URLs in Room; image requests are routed using the current Worker setting.
                return ReaderArticle(Jsoup.parse(string("title", "headline")).text(), plain.take(60_000),
                    images.firstOrNull().orEmpty(), original,
                    publishedAt = runCatching { Instant.parse(string("publishedAt", "datePublished")).toEpochMilli() }.getOrNull(),
                    author = string("author"), images = images.distinct())
            }
            val document = Jsoup.parse(raw, original)
            val schema = document.select("script[type=application/ld+json]").asSequence().mapNotNull {
                runCatching { articleSchema(org.json.JSONTokener(it.data()).nextValue()) }.getOrNull()
            }.firstOrNull()
            val body = document.selectFirst("[itemprop=articleBody]") ?: document.selectFirst(".article__body")
                ?: document.selectFirst(".b-article") ?: document.selectFirst("article")
                ?: document.selectFirst("main") ?: document.body()
            body.select("template,amp-ad,amp-sticky-ad,amp-analytics,amp-auto-ads,amp-sidebar,.b-related,.b-article-info").remove()
            val images = body.select("img[src],amp-img[src]").map { it.absUrl("src") }
                .filter { it.startsWith("https://") }.toMutableList()
            fun schemaImages(value: Any?): List<String> = when (value) {
                is String -> listOf(value)
                is JSONObject -> listOf(value.optString("url"))
                is JSONArray -> (0 until value.length()).flatMap { schemaImages(value.opt(it)) }
                else -> emptyList()
            }
            images += schemaImages(schema?.opt("image")).filter { it.isNotBlank() }.mapNotNull {
                runCatching { java.net.URI(original).resolve(it).toString() }.getOrNull()
            }.filter { it.startsWith("https://") }
            // Article photos and structured article covers precede generic sharing banners.
            document.selectFirst("meta[property=og:image]")?.attr("content")?.let {
                val url = runCatching { java.net.URI(original).resolve(it).toString() }.getOrNull()
                if (url != null && url.startsWith("https://")) images += url
            }
            val title = document.selectFirst("h1")?.text()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: schema?.optString("headline")?.takeIf { it.isNotBlank() } ?: document.title()
            val links = ArticleLinks.extract(body)
            val date = sequenceOf(document.selectFirst("meta[property=article:published_time],meta[name=date]")?.attr("content"),
                document.selectFirst("time[datetime]")?.attr("datetime"), schema?.optString("datePublished"))
                .filterNotNull().firstNotNullOfOrNull { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
            fun authorName(value: Any?): String = when (value) {
                is String -> value
                is JSONObject -> value.optString("name")
                is JSONArray -> (0 until value.length()).map { authorName(value.opt(it)) }.filter { it.isNotBlank() }.joinToString(", ")
                else -> ""
            }
            val author = document.selectFirst("meta[name=author]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: authorName(schema?.opt("author"))
            return ReaderArticle(title, htmlText(body).take(60_000), images.firstOrNull().orEmpty(), original,
                publishedAt = date,
                author = author, images = images.distinct(), links = links)
        }

        private fun articleSchema(value: Any?, depth: Int = 0): JSONObject? {
            if (depth > 8) return null
            if (value is JSONArray) return (0 until value.length()).firstNotNullOfOrNull { articleSchema(value.opt(it), depth + 1) }
            if (value !is JSONObject) return null
            val type = value.opt("@type")
            val types = if (type is JSONArray) (0 until type.length()).map { type.optString(it) } else listOf(type as? String ?: "")
            if (types.any { it.substringAfterLast('/').let { name -> name.endsWith("Article") || name == "BlogPosting" } }) return value
            return articleSchema(value.opt("@graph"), depth + 1)
        }
    }
}
