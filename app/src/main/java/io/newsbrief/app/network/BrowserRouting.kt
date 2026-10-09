package io.newsbrief.app.network

import java.net.URI

/** Apply the same transport to main documents, redirects and embedded resources. */
object BrowserRouting {
    data class Route(val type: String, val forceProxy: Boolean)

    fun route(url: String, mainFrame: Boolean, documentUrl: String, accept: String = "", forceDocumentProxy: Boolean = false): Route {
        require(ArticleLinks.valid(url)) { "Поддерживаются только HTTPS-страницы" }
        val path = URI(url).path.orEmpty().lowercase(java.util.Locale.ROOT)
        val image = accept.startsWith("image/", ignoreCase = true) ||
            Regex(".*\\.(?:png|jpe?g|gif|webp|avif|svg|ico)$").matches(path)
        return Route(if (image) "image" else "fetch",
            forceDocumentProxy || image || NewsRouting.requiresProxy(url) || (!mainFrame && NewsRouting.requiresProxy(documentUrl)))
    }
}
