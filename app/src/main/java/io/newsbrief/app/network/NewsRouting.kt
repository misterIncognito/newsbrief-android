package io.newsbrief.app.network

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Configurable transport for individual news sources; no app-wide VPN needed. */
object NewsRouting {
    const val DEFAULT_WORKER = "https://newsbrief-proxy.a0-07d.workers.dev"

    private val proxyRequired = setOf("meduza.io", "zerkalo.io")

    fun requiresProxy(articleOrFeedUrl: String): Boolean {
        val host = runCatching { URI(articleOrFeedUrl).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return proxyRequired.any { host == it || host.endsWith(".$it") } ||
            Regex("(?:[a-z0-9-]+\\.)?zerkalo-[a-z0-9-]+\\.site").matches(host)
    }

    /** Force proxy for images belonging to an article whose origin is proxy-required. */
    fun endpoint(resourceUrl: String, type: String, workerBase: String, forceProxy: Boolean = false): String {
        require(type in setOf("fetch", "article", "image")) { "Unsupported endpoint" }
        val resource = URI(resourceUrl)
        require(resource.scheme.equals("https", true) && !resource.host.isNullOrBlank() &&
            resource.userInfo == null) { "Only HTTPS resource URLs are allowed" }
        if (!forceProxy && !requiresProxy(resourceUrl)) return resourceUrl
        val worker = URI(workerBase.trimEnd('/'))
        require(worker.scheme.equals("https", true) && worker.host != null && worker.userInfo == null &&
            worker.port == -1 && worker.rawQuery == null && worker.rawFragment == null) { "Invalid HTTPS Worker URL" }
        val encoded = URLEncoder.encode(resourceUrl, StandardCharsets.UTF_8.name())
        return "${worker.toASCIIString()}/$type?url=$encoded"
    }
}
