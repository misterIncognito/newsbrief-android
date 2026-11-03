package io.newsbrief.app.network

import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.requireLocalized
import java.net.URI

/** Apply the same transport to main documents, redirects and embedded resources. */
object BrowserRouting {
    data class Route(val type: String, val forceProxy: Boolean)

    /** Original-page mode is an explicit choice; required proxy hosts never become direct. */
    fun canLoadOriginal(url: String, documentUrl: String, proxyLocked: Boolean = false): Boolean =
        ArticleLinks.valid(url) && !proxyLocked && !NewsRouting.requiresProxy(url) &&
            !NewsRouting.requiresProxy(documentUrl)

    fun route(url: String, mainFrame: Boolean, documentUrl: String, accept: String = "", forceDocumentProxy: Boolean = false): Route {
        requireLocalized(ArticleLinks.valid(url), R.string.nb_browser_https_only, "Поддерживаются только HTTPS-страницы")
        val path = URI(url).path.orEmpty().lowercase(java.util.Locale.ROOT)
        val image = accept.startsWith("image/", ignoreCase = true) ||
            Regex(".*\\.(?:png|jpe?g|gif|webp|avif|svg|ico)$").matches(path)
        return Route(if (image) "image" else "fetch",
            forceDocumentProxy || image || NewsRouting.requiresProxy(url) || (!mainFrame && NewsRouting.requiresProxy(documentUrl)))
    }
}
