package io.newsbrief.app.network

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

data class ArticleLink(val label: String, val url: String)

/** Keep public web links as data; opening them always uses the native reader. */
object ArticleLinks {
    fun valid(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

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

    fun telegramPreview(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "t.me" || uri.userInfo != null) return null
        val post = uri.path.removePrefix("/s/").removePrefix("/")
        if (post.matches(Regex("[A-Za-z0-9_]+/[0-9]+"))) "https://t.me/s/$post" else null
    }.getOrNull()

    /** Prefer publisher articles to Telegram cross-promotions and app-store links. */
    fun fullArticle(links: List<ArticleLink>): ArticleLink? = links.firstOrNull {
        val host = URI(it.url).host.lowercase()
        host in setOf("devby.io", "dev.by") || NewsRouting.requiresProxy(it.url)
    }
}
