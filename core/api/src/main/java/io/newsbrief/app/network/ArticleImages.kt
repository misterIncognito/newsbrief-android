package io.newsbrief.app.network

import org.json.JSONArray

object ArticleImages {
    fun decode(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    fun originalUrl(url: String, worker: String): String =
        io.newsbrief.app.runtime.FeatureBindings.create().pageProxyPlugins.firstNotNullOfOrNull { it.originalResource(url, worker) } ?: url
}
