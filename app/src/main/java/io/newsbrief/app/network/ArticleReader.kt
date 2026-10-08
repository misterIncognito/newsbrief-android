package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SettingsStore
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

/** The worker's /article contract can evolve without changing our Room schema. */
data class ReaderArticle(
    val title: String,
    val text: String,
    val imageUrl: String,
    val originalUrl: String,
    val notice: String? = null
)

class ArticleReader(private val settings: SettingsStore) {
    suspend fun load(article: ArticleEntity): ReaderArticle = withContext(Dispatchers.IO) {
        var title = article.title
        var body = article.content.ifBlank { article.excerpt }
        var image = article.imageUrl
        var warning: String? = null
        // Atom can already contain full articles; avoid unnecessary fetches.
        if (article.content.length < 350 && article.link.startsWith("https://") &&
            !article.link.startsWith("https://t.me/")) {
            try {
                val route = NewsRouting.endpoint(article.link, "article", settings.workerUrl())
                val raw = download(route, 2_500_000).toString(Charsets.UTF_8)
                val result = parseArticle(raw, article.link)
                if (result.text.length > body.length) body = result.text
                if (result.title.isNotBlank()) title = result.title
                if (image.isBlank()) image = result.imageUrl
            } catch (e: Exception) {
                warning = "Не удалось загрузить полную статью: ${e.message.orEmpty().take(90)}"
            }
        }
        ReaderArticle(title, body, image, article.link, warning)
    }

    companion object {
        /** Supports JSON {title,text, image}, {article:{...}} and HTML for direct sources. */
        fun parseArticle(raw: String, original: String): ReaderArticle {
            if (raw.trimStart().startsWith("{")) {
                val root = JSONObject(raw)
                val nested = root.optJSONObject("article") ?: root.optJSONObject("data") ?: root
                fun string(vararg keys: String): String = keys.asSequence()
                    .map { nested.optString(it).trim() }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
                val rawBody = string("text", "content", "body", "articleBody", "contentText", "content_text", "fullText", "markdown", "html", "description", "excerpt")
                val imageObj = nested.optJSONObject("image")
                val image = string("imageUrl", "image_url", "image", "leadImage", "lead_image_url", "thumbnail")
                    .takeIf { it.startsWith("https://") }
                    ?: imageObj?.optString("url").orEmpty().ifBlank {
                        nested.optJSONArray("images")?.optString(0).orEmpty()
                    }
                return ReaderArticle(
                    title = Jsoup.parse(string("title", "headline")).text(),
                    text = Jsoup.parse(rawBody).text().take(60_000),
                    imageUrl = image.takeIf { it.startsWith("https://") }.orEmpty(),
                    originalUrl = original
                )
            }
            val document = Jsoup.parse(raw, original)
            document.select("script,style,nav,footer,aside,header,form").remove()
            val body = document.selectFirst("article") ?: document.selectFirst("main") ?: document.body()
            val image = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            return ReaderArticle(
                title = document.title(),
                text = body.text().take(60_000),
                imageUrl = image.takeIf { it.startsWith("https://") }.orEmpty(),
                originalUrl = original
            )
        }

        fun download(url: String, maxBytes: Int): ByteArray {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = false // avoid Worker routing surprises
                setRequestProperty("Accept", "application/json, text/html, image/*, */*")
                setRequestProperty("User-Agent", "NewsBrief/0.4")
            }
            try {
                val code = conn.responseCode
                check(code in 200..299) { "HTTP $code" }
                if (conn.contentLengthLong > maxBytes) error("Response too large")
                return conn.inputStream.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8_192)
                    var size = 0
                    while (true) {
                        val count = stream.read(buf)
                        if (count < 0) break
                        size += count
                        require(size <= maxBytes) { "Response too large" }
                        out.write(buf, 0, count)
                    }
                    out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
