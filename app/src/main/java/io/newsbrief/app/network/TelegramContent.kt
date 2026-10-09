package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity

/** Reader port for source-owned Telegram content; the caller supplies policy-bound transport. */
interface TelegramContent {
    fun supports(article: ArticleEntity): Boolean = true
    suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?,
        fetch: (String, String, String, Int) -> ByteArray): ReaderArticle
}

class CombinedTelegramContent(private val plugins: List<TelegramContent>) : TelegramContent {
    override fun supports(article: ArticleEntity) = plugins.any { it.supports(article) }
    override suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?, fetch: (String, String, String, Int) -> ByteArray) =
        (plugins.firstOrNull { it.supports(article) } ?: error("Telegram content plugin unavailable")).load(article, cached, preview, fetch)
}
