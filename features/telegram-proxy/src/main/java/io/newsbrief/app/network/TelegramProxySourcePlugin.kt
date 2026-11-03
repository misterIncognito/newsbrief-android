package io.newsbrief.app.network

import io.newsbrief.modules.features.telegram_proxy.R
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules

class TelegramProxySourcePlugin(private val client: TelegramProxyClient) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.TELEGRAM_PROXY, "Telegram via proxy", "@channel, t.me link or channel ID", titleResource = R.string.nb_telegram_proxy_source_title, addressHintResource = R.string.nb_telegram_proxy_source_hint)
    override fun canonicalAddress(input: String) = "telegram-proxy:${TelegramProxyClient.channelKey(input)}"
    override suspend fun collect(source: SourceEntity) = client.posts(source)
    override suspend fun collectSince(source: SourceEntity) = client.postsSince(source)
    override suspend fun discover() = client.channels()
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity) = incoming.copy(
        id = existing.id, importedAt = existing.importedAt, readAt = existing.readAt,
        imageUrl = incoming.imageUrl.ifBlank { existing.imageUrl },
        imagesJson = if (incoming.imagesJson == "[]") existing.imagesJson else incoming.imagesJson,
        proxyRequired = true)

}
