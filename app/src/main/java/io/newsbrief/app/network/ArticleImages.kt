package io.newsbrief.app.network

import org.json.JSONArray
import java.net.URI
import java.net.URLDecoder

object ArticleImages {
    fun decode(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    fun originalUrl(url: String, worker: String): String {
        if (!url.startsWith(worker.trimEnd('/') + "/image?")) return url
        return URI(url).rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "url" }
            ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") } ?: url
    }
}
