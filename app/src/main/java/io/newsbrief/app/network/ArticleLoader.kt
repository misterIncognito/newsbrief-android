package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity

interface ArticleLoader {
    suspend fun load(article: ArticleEntity, refresh: Boolean = false): ReaderArticle
}
