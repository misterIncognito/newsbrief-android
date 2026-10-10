package io.newsbrief.app.network

import io.newsbrief.app.data.*

class TelegramProxyContent(private val client: TelegramProxyClient) : TelegramContent {
    override fun supports(article: ArticleEntity) = article.sourcePluginId == SourceKind.TELEGRAM_PROXY
    override suspend fun load(article: ArticleEntity, cached: ReaderArticle, preview: String?, fetch: (String, String, String, Int) -> ByteArray) = client.post(article)
}
