package io.newsbrief.app.network

import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.LocalizedFailure
import io.newsbrief.app.util.requireLocalized
import io.newsbrief.app.data.*
import org.jsoup.Jsoup

class TelegramArticleContent(private val settings: SourceSettings) : TelegramContent {
    override fun supports(article: ArticleEntity) = article.sourcePluginId != SourceKind.TELEGRAM_PROXY && ArticleLinks.telegramPreview(article.link) != null
    override suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?,
        fetch: (String, String, String, Int) -> ByteArray): ReaderArticle {
        requireNotNull(preview)
        val raw = fetch(preview, "fetch", settings.workerUrl(), 2_500_000).toString(Charsets.UTF_8)
        val post = TelegramPreviewParser().parse(raw, SourceEntity(id = article.sourceId,
            name = article.sourceName, address = preview, kind = SourceKind.TELEGRAM))
            .firstOrNull { it.link == article.link } ?: throw LocalizedFailure(R.string.nb_err_tg_post_unavailable, "Публикация Telegram недоступна")
        // Older versions accidentally stored the quoted reply instead of the message body.
        // Only replace cached text when it matches that exact quoted preview.
        val postId = article.link.substringAfterLast("/", "")
        val message = Jsoup.parse(raw, preview).select(".tgme_widget_message[data-post]")
            .firstOrNull { it.attr("data-post").substringAfterLast('/') == postId && "https://t.me/" + it.attr("data-post") == article.link }
        val quoted = message?.selectFirst(".tgme_widget_message_reply .tgme_widget_message_text")?.let { FeedMarkup.htmlText(it) }
        val repair = !quoted.isNullOrBlank() && quoted == article.content && post.content.isNotBlank() && post.content != quoted
        return cached.copy(title = if (repair) post.title else cached.title,
            text = if (repair) post.content else article.content.ifBlank { post.content },
            imageUrl = cached.imageUrl.ifBlank { post.imageUrl },
            images = cached.images.ifEmpty { ArticleImages.decode(post.imagesJson) },
            links = ArticleLinks.decode(post.linksJson))
    }
}
