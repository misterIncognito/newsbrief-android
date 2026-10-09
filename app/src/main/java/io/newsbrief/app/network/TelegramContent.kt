package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity

/** Reader port for source-owned Telegram content; the caller supplies policy-bound transport. */
interface TelegramContent {
    suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?,
        fetch: (String, String, String, Int) -> ByteArray): ReaderArticle
}
