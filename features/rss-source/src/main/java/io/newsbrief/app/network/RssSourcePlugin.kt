package io.newsbrief.app.network

import io.newsbrief.modules.features.rss_source.R
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules

class RssSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.RSS, "RSS / Atom", "HTTPS feed URL", addressHintResource = R.string.nb_rss_address_hint)
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = FeedParsers().parseFeed(fetch(source.address), source)
}
