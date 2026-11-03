package io.newsbrief.app.network

import io.newsbrief.app.data.FeedCategory

/** Search indexes, rather than a locally invented list of thousands of sources. */
enum class DirectoryProvider(val label: String) { RSS("RSS / сайты"), TELEGRAM("Telegram"), CURATED("Рекомендации") }
data class DirectorySource(
    val title: String, val address: String, val kind: String, val description: String = "",
    val language: String = "", val audience: Long? = null, val categories: Set<FeedCategory> = emptySet(),
    val topics: Set<String> = emptySet()
)
data class DirectoryResults(
    val items: List<DirectorySource>, val total: Int? = null, val directorySize: Int? = null,
    val cached: Boolean = false, val notice: String? = null,
    val category: String? = null, val nextOffset: Int? = null, val noticeResource: Int? = null
)
interface DirectoryNetwork {
    suspend fun search(provider: DirectoryProvider, query: String): DirectoryResults
    suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults =
        error("У этого поиска нет следующих страниц")
}

/** Topic shortcuts browse categories; specific names use the provider's working search tool. */
object TelegramTopics {
    fun category(query: String): String? = when (query.trim().lowercase(java.util.Locale.ROOT)) {
        "ai", "ии", "artificial intelligence", "искусственный интеллект", "нейросети" -> "ai"
        "tech", "technology", "technologies", "технологии" -> "tech"
        "news", "новости" -> "news"
        "business", "бизнес" -> "business"
        "programming", "coding", "программирование", "код" -> "dev"
        else -> null
    }
    fun show(source: DirectorySource, hidePromotions: Boolean): Boolean =
        !hidePromotions || source.topics.none { it == "crypto" || it == "gaming" }
    fun merge(previous: DirectoryResults, next: DirectoryResults): DirectoryResults {
        require(previous.category != null && previous.category == next.category) { "Категория изменилась" }
        require(next.nextOffset == null || next.nextOffset > (previous.nextOffset ?: -1)) { "Каталог повторил страницу" }
        return next.copy(items = (previous.items + next.items).distinctBy { it.address.lowercase(java.util.Locale.ROOT) },
            cached = previous.cached || next.cached)
    }
}

