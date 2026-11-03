package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity

data class SourceBatch(val articles: List<ArticleEntity>, val nextCursor: String? = null, val historyGap: Boolean = false)

interface NewsCollector {
    fun supports(source: SourceEntity): Boolean = true
    suspend fun collect(source: SourceEntity): List<ArticleEntity>
    suspend fun collectSince(source: SourceEntity): SourceBatch = SourceBatch(collect(source))
    fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity = existing
}
