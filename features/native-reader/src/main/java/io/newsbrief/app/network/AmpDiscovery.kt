package io.newsbrief.app.network

import org.jsoup.Jsoup
import java.net.URI

/** Follow the publisher's advertised AMP page once; never invent an AMP URL. */
object AmpDiscovery {
    fun alternative(html: String, original: String): String? {
        val document = Jsoup.parse(html, original)
        val root = document.selectFirst("html") ?: return null
        if (root.hasAttr("amp") || root.hasAttr("⚡")) return null
        return document.select("head link[href]").asSequence()
            .filter { link -> link.attr("rel").split(Regex("\\s+")).any { it.equals("amphtml", true) } }
            .mapNotNull { link ->
                runCatching {
                    val advertised = URI(link.attr("href").trim())
                    require((advertised.scheme == null || advertised.scheme.equals("https", true)) && advertised.userInfo == null)
                    val url = URI(link.absUrl("href")).normalize()
                    require(url.scheme.equals("https", true) && !url.host.isNullOrBlank() && url.userInfo == null)
                    url.toASCIIString().substringBefore('#').takeIf { it != original.substringBefore('#') }
                }.getOrNull()
            }.firstOrNull()
    }
}
