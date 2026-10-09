package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity

interface PrefetchArticleStore {
    suspend fun lookup(url: String): ArticleEntity?
    suspend fun prefetchCandidates(): List<ArticleEntity>
    suspend fun linkedArticle(parent: ArticleEntity, url: String): ArticleEntity
    suspend fun readArticle(article: ArticleEntity, refresh: Boolean = false,
        preserveLongerText: Boolean = false): ReaderArticle
}

interface PrefetchImageStore {
    fun contains(url: String): Boolean
    suspend fun load(url: String, worker: String): ByteArray
}
