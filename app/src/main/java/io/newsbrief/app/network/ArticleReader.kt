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

data class ReaderArticle(
    val title: String,
    val text: String,
    val imageUrl: String,
    val originalUrl: String,
    val notice: String? = null,
    val publishedAt: Long? = null,
    val author: String = "",
    val images: List<String> = emptyList()
)

class ArticleReader(private val settings: SettingsStore) {
    suspend fun load(article: ArticleEntity, refresh: Boolean = false): ReaderArticle = withContext(Dispatchers.IO) {
        val cached = ReaderArticle(article.title, article.content.ifBlank { article.excerpt },
            article.imageUrl, article.link, publishedAt = article.publishedAt, author = article.author,
            images = decodeImages(article.imagesJson))
        // Full feed content and saved reader text are usable without a network request.
        if ((!refresh && (article.readerLoaded || article.content.isNotBlank())) ||
            article.link.startsWith("https://t.me/")) return@withContext cached
        try {
            val raw = NewsHttp.download(article.link, "article", settings.workerUrl(), 2_500_000)
                .toString(Charsets.UTF_8)
            val result = parseArticle(raw, article.link)
            require(result.text.isNotBlank()) { "Article text unavailable" }
            result.copy(title = result.title.ifBlank { cached.title },
                imageUrl = result.imageUrl.ifBlank { cached.imageUrl },
                publishedAt = result.publishedAt ?: cached.publishedAt)
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

        fun htmlText(element: Element): String {
            element.select("script,style,nav,footer,aside,form").remove()
            element.select("p,div,h1,h2,h3,h4,li,blockquote,br").forEach { it.appendText("\n\n") }
            return element.wholeText().replace(Regex("[\\t ]+"), " ")
                .replace(Regex(" *\n *"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }

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
            val images = document.select("article img[src],main img[src]").map { it.absUrl("src") }
                .filter { it.startsWith("https://") }.toMutableList()
            document.selectFirst("meta[property=og:image]")?.attr("content")?.let {
                val url = java.net.URI(original).resolve(it).toString()
                if (url.startsWith("https://")) images.add(0, url)
            }
            val body = document.selectFirst("article") ?: document.selectFirst("main") ?: document.body()
            return ReaderArticle(document.selectFirst("h1")?.text() ?: document.title(),
                htmlText(body).take(60_000), images.firstOrNull().orEmpty(), original, images = images.distinct())
        }
    }
}
