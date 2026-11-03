package io.newsbrief.app.network

import io.newsbrief.modules.features.rss_source.R
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI

class RssSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.RSS, "RSS / Atom", "HTTPS feed URL", addressHintResource = R.string.nb_rss_address_hint)
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = FeedParsers().parseFeed(fetch(source.address), source)

    override suspend fun collectSince(source: SourceEntity): SourceBatch {
        val parser = FeedParsers()
        val previous = source.fetchCursor.takeIf { it.isNotBlank() }
        val posts = linkedMapOf<String, ArticleEntity>()
        val visited = mutableSetOf<String>()
        var url = source.address
        var newest: String? = null
        repeat(MAX_PAGES) {
            if (!visited.add(url)) return SourceBatch(posts.values.toList(), historyGap = previous != null)
            val xml = fetch(url)
            val page = parser.parseFeed(xml, source)
            if (newest == null) newest = page.firstOrNull()?.link
            page.forEach { posts.putIfAbsent(it.link, it) }
            if (previous == null || page.any { it.link == previous }) {
                return SourceBatch(posts.values.toList(), newest)
            }
            // Follow only explicit feed-level next links on the same HTTPS origin.
            url = nextPage(xml, url, source.address)
                ?: return SourceBatch(posts.values.toList(), historyGap = true)
        }
        return SourceBatch(posts.values.toList(), historyGap = previous != null)
    }

    private fun nextPage(xml: String, current: String, original: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var insideEntry = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DTD is not allowed in feeds")
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(java.util.Locale.ROOT)
                    if (name == "item" || name == "entry") insideEntry = true
                    if (!insideEntry && name == "link" && parser.getAttributeValue(null, "rel")?.equals("next", true) == true) {
                        val href = parser.getAttributeValue(null, "href") ?: return null
                        val base = URI(original)
                        val target = runCatching { URI(current).resolve(href) }.getOrNull() ?: return null
                        if (!target.scheme.equals("https", true) || target.userInfo != null || target.fragment != null ||
                            !target.host.equals(base.host, true) || target.port != base.port) return null
                        return target.toString()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':').lowercase(java.util.Locale.ROOT) in setOf("item", "entry")) insideEntry = false
            }
            parser.next()
        }
        return null
    }
    companion object { private const val MAX_PAGES = 100 }
}
