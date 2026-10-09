package io.newsbrief.app.network

import java.net.URI
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

    /** Compatibility API; selected page-proxy plugins supply endpoint implementations. */
    fun endpoint(resourceUrl: String, type: String, workerBase: String, forceProxy: Boolean = false) =
        PageRouting.route(resourceUrl, type, workerBase, forceProxy)
}
