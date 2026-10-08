package io.newsbrief.app.data

/** Categories describe sources, so no network or AI classification is needed offline. */
enum class FeedCategory(val label: String) {
    ALL("Все"), TECH("Технологии"), BUSINESS("Бизнес"), BELARUS("Беларусь"),
    POLAND("Польша"), WORLD("Мир"), GENERAL("Общие новости"), OTHER("Другие")
}

data class FeedSourceOption(val key: String, val label: String, val ids: List<Long>)

object FeedFilters {
    fun categories(source: SourceEntity): Set<FeedCategory> {
        val category = SourceCatalog.presets.firstOrNull { it.address == source.address }?.category
            ?: return setOf(FeedCategory.OTHER)
        return buildSet {
            if (category.contains("Технологии") || category.contains("IT")) add(FeedCategory.TECH)
            if (category.contains("Бизнес")) add(FeedCategory.BUSINESS)
            if (category.contains("Беларусь")) add(FeedCategory.BELARUS)
            if (category.contains("Польша") || category.contains("Варшава")) add(FeedCategory.POLAND)
            if (category.contains("Мир")) add(FeedCategory.WORLD)
            if (isEmpty()) add(FeedCategory.GENERAL)
        }
    }

    private fun publisher(source: SourceEntity): Pair<String, String> = when (source.address) {
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
    fun sourceIds(sources: List<SourceEntity>, category: FeedCategory, publisherKey: String?): List<Long>? {
        if (category == FeedCategory.ALL && publisherKey == null) return null
        return options(sources, category).filter { publisherKey == null || it.key == publisherKey }
            .flatMap { it.ids }.sorted()
    }
}
