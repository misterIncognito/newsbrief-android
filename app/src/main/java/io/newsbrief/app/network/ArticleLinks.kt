package io.newsbrief.app.network

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

data class ArticleLink(val label: String, val url: String)

/** Keep public web links as data; opening them always uses the native reader. */
object ArticleLinks {
    fun valid(url: String): Boolean = ProxyUrls.validResource(url)

    fun extract(element: Element): List<ArticleLink> = element.select("a[href]")
        .map { ArticleLink(it.text().trim().ifBlank { it.absUrl("href") }, it.absUrl("href")) }
        .filter { valid(it.url) }.distinctBy { it.url }.take(50)

    fun encode(links: List<ArticleLink>): String = JSONArray().apply {
        links.filter { valid(it.url) }.distinctBy { it.url }.forEach {
            put(JSONObject().put("label", it.label).put("url", it.url))
        }
    }.toString()

    fun decode(raw: String): List<ArticleLink> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull {
            val item = array.optJSONObject(it) ?: return@mapNotNull null
            val url = item.optString("url")
            if (valid(url)) ArticleLink(item.optString("label").ifBlank { URI(url).host }, url) else null
        }.distinctBy { it.url }.take(50)
    }.getOrDefault(emptyList())

    fun isTelegramPost(article: io.newsbrief.app.data.ArticleEntity): Boolean =
        article.sourcePluginId == io.newsbrief.app.data.SourceKind.TELEGRAM_PROXY || telegramPreview(article.link) != null

    fun telegramPreview(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "t.me" || uri.userInfo != null) return null
        val post = uri.path.removePrefix("/s/").removePrefix("/")
        if (post.matches(Regex("[A-Za-z0-9_]+/[0-9]+"))) "https://t.me/s/$post" else null
    }.getOrNull()

    /** Prefer publisher mirrors, then other article links, over social/app promotions. */
    fun fullArticle(links: List<ArticleLink>): ArticleLink? {
        val safe = links.filter { valid(it.url) }
        return safe.firstOrNull { URI(it.url).host.endsWith(".site") && NewsRouting.requiresProxy(it.url) }
            ?: safe.firstOrNull { URI(it.url).host.lowercase(java.util.Locale.ROOT) in setOf("devby.io", "dev.by") || NewsRouting.requiresProxy(it.url) }
            ?: safe.firstOrNull {
                val uri = URI(it.url)
                val host = uri.host.lowercase(java.util.Locale.ROOT)
                val promotions = setOf("t.me", "telegram.me", "apps.apple.com", "play.google.com", "donorbox.org", "x.com", "twitter.com", "www.tiktok.com")
                host !in promotions && uri.path.orEmpty().trim('/').isNotBlank()
            }
    }
}
