package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity

interface NewsCollector {
    suspend fun collect(source: SourceEntity): List<ArticleEntity>
    fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity = existing
}
