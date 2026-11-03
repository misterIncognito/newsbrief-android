package io.newsbrief.app.data

object FeedFilters {
    private fun catalogAddress(source: SourceEntity): String =
        if (source.kind == SourceKind.TELEGRAM_PROXY && source.address.startsWith("telegram-proxy:"))
            "https://t.me/s/" + source.address.removePrefix("telegram-proxy:") else source.address

    fun categories(source: SourceEntity): Set<FeedCategory> {
        val saved = runCatching {
            val tags = org.json.JSONArray(source.categoriesJson)
            (0 until tags.length()).mapNotNull { runCatching { FeedCategory.valueOf(tags.getString(it)) }.getOrNull() }.toSet()
        }.getOrDefault(emptySet())
        if (saved.isNotEmpty()) return saved
        val address = catalogAddress(source)
        return when {
            address.contains("habr.com/") || address.contains("theverge.com/") || address.contains("vcnews") -> setOf(FeedCategory.TECH)
            address.contains("businessinsider") || address.contains("businessinsider.com") -> setOf(FeedCategory.BUSINESS)
            address.contains("zerkalo") || address.contains("nashaniva") || address.contains("belsat") -> setOf(FeedCategory.BELARUS)
            address.contains("polsat") || address.contains("thewwarsaw") || address.contains("notesfrompoland") || address.contains("warsawinsider") -> setOf(FeedCategory.POLAND)
            address.contains("bbc.co.uk/world") || address.contains("theguardian") || address.contains("aljazeera") -> setOf(FeedCategory.WORLD)
            else -> setOf(FeedCategory.OTHER)
        }
    }

    private fun publisher(source: SourceEntity): Pair<String, String> = when (catalogAddress(source)) {
        "https://news.zerkalo.io/rss/all.rss", "https://t.me/s/zerkalo_io" -> "zerkalo" to "Зеркало"
        "https://feeds.bbci.co.uk/russian/rss.xml", "https://t.me/s/bbcrussian" -> "bbc-ru" to "BBC Русская служба"
        "https://meduza.io/rss/all", "https://t.me/s/meduzalive" -> "meduza" to "Медуза"
        "https://rss.dw.com/rdf/rss-ru-all", "https://t.me/s/dwglavnoe" -> "dw" to "Deutsche Welle"
        "https://habr.com/ru/rss/articles/?fl=ru", "https://habr.com/ru/rss/hubs/artificial_intelligence/articles/?fl=ru" -> "habr" to "Хабр"
        "https://www.polsatnews.pl/rss/polska.xml", "https://www.polsatnews.pl/rss/wszystkie.xml" -> "polsat" to "Polsat News"
        "https://businessinsider.com.pl/.feed",
        "https://news.google.com/rss/search?q=site%3Abusinessinsider.com&hl=en-US&gl=US&ceid=US%3Aen" -> "business-insider" to "Business Insider"
        else -> "source:${source.id}" to source.name
    }

    fun options(sources: List<SourceEntity>, category: FeedCategory): List<FeedSourceOption> = sources
        .filter { it.enabled && (category == FeedCategory.ALL || category in categories(it)) }
        .groupBy { publisher(it) }.map { (publisher, members) ->
            FeedSourceOption(publisher.first, publisher.second, members.map { it.id }.sorted())
        }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }

    /** null means all enabled sources; an empty list intentionally means no matching sources. */
    fun sourceIds(sources: List<SourceEntity>, category: FeedCategory, publisherKey: String?): List<Long>? =
        sourceIds(sources, category, publisherKey?.let { setOf(it) }.orEmpty())

    fun sourceIds(sources: List<SourceEntity>, category: FeedCategory, publisherKeys: Set<String>): List<Long>? {
        if (category == FeedCategory.ALL && publisherKeys.isEmpty()) return null
        return options(sources, category).filter { publisherKeys.isEmpty() || it.key in publisherKeys }
            .flatMap { it.ids }.distinct().sorted()
    }
}
