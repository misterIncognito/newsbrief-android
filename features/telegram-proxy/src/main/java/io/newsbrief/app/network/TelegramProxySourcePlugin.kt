package io.newsbrief.app.network

import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules

class TelegramProxySourcePlugin(private val client: TelegramProxyClient) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.TELEGRAM_PROXY, "Telegram через прокси", "@канал, ссылка t.me или ID канала")
    override fun canonicalAddress(input: String) = "telegram-proxy:${TelegramProxyClient.channelKey(input)}"
    override suspend fun collect(source: SourceEntity) = client.posts(source)
    override suspend fun discover() = client.channels()
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity) = incoming.copy(
        id = existing.id, importedAt = existing.importedAt,
        imageUrl = incoming.imageUrl.ifBlank { existing.imageUrl },
        imagesJson = if (incoming.imagesJson == "[]") existing.imagesJson else incoming.imagesJson,
        proxyRequired = true)

}
