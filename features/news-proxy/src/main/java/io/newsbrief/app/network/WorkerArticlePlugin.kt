package io.newsbrief.app.network

class WorkerArticlePlugin : ArticleFetchPlugin {
    override val id = "worker-readability"
    override fun supports(request: ArticleFetchRequest) = request.mustProxy && !ArticleLinks.isTelegramPost(request.article)
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle = request.parseArticle(request.download(request.article.link, "article"), request.article.link).let { reader ->
            val fallback = request.originalReader
            reader.copy(title = reader.title.ifBlank { fallback.title }, imageUrl = reader.imageUrl.ifBlank { fallback.imageUrl },
                author = reader.author.ifBlank { fallback.author }, publishedAt = reader.publishedAt ?: fallback.publishedAt,
                images = reader.images.ifEmpty { fallback.images })
        }
}
