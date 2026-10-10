package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity

/** Optional extraction extension; transport callbacks enforce the existing routing policy. */
interface ArticleExtractor {
    suspend fun fetch(article: ArticleEntity, cached: ReaderArticle, worker: String,
        direct: (String, String, String, Int) -> ByteArray,
        proxy: (String, String, String, Int) -> ByteArray): ReaderArticle
}
