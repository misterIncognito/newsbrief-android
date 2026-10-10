package io.newsbrief.app.network

import io.newsbrief.app.data.*

class TelegramArticleContent(private val settings: SourceSettings) : TelegramContent {
    override fun supports(article: ArticleEntity) = article.sourcePluginId != SourceKind.TELEGRAM_PROXY && ArticleLinks.telegramPreview(article.link) != null
    override suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?,
        fetch: (String, String, String, Int) -> ByteArray): ReaderArticle {
        requireNotNull(preview)
        val raw = fetch(preview, "fetch", settings.workerUrl(), 2_500_000).toString(Charsets.UTF_8)
        val post = TelegramPreviewParser().parse(raw, SourceEntity(id = article.sourceId,
            name = article.sourceName, address = preview, kind = SourceKind.TELEGRAM))
            .firstOrNull { it.link == article.link } ?: error("Публикация Telegram недоступна")
        return cached.copy(text = article.content.ifBlank { post.content },
            imageUrl = cached.imageUrl.ifBlank { post.imageUrl },
            images = cached.images.ifEmpty { ArticleImages.decode(post.imagesJson) },
            links = ArticleLinks.decode(post.linksJson))
    }
}
