package io.newsbrief.app.network

/** Shared result contract for Telegram posts and native article extraction. */
data class ReaderArticle(
    val title: String,
    val text: String,
    val imageUrl: String,
    val originalUrl: String,
    val notice: String? = null,
    val publishedAt: Long? = null,
    val author: String = "",
    val images: List<String> = emptyList(),
    val links: List<ArticleLink> = emptyList()
)

