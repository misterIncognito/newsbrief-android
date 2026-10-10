package io.newsbrief.app.network

import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules

class RssSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.RSS, "RSS / Atom", "HTTPS-ссылка на RSS или Atom")
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = FeedParsers().parseFeed(fetch(source.address), source)
}
