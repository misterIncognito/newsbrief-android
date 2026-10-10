package io.newsbrief.app.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Cloudflare Worker implementation of the generic page-proxy plugin contract. */
class CloudflarePageProxy : PageProxyPlugin {
    override val id = "news-worker"
    override fun supports(request: PageRequest) = (request.requiresProxy || request.preferProxy) && request.type in setOf("fetch", "article", "image")
    override fun originalResource(url: String, configuration: String): String? {
        val prefix = configuration.trimEnd('/') + "/image?"
        if (!url.startsWith(prefix)) return null
        return java.net.URI(url).rawQuery?.split('&')?.firstOrNull { it.startsWith("url=") }
            ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
    }
    override fun endpoint(request: PageRequest, configuration: String): String {
        require(ProxyUrls.validResource(request.resource))
        require(supports(request))
        val worker = ProxyUrls.workerService(configuration)
        return "${worker.toASCIIString()}/${request.type}?url=" + URLEncoder.encode(request.resource, StandardCharsets.UTF_8.name())
    }
}
