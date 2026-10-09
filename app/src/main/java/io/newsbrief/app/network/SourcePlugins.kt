package io.newsbrief.app.network

import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourcePluginRegistry(plugins: List<NewsSourcePlugin>) : SourceRegistry {
    private val byId = plugins.associateBy { it.info.id }
    override val available = plugins.map { it.info }
    init { require(byId.size == plugins.size) { "Duplicate source plugin ID" } }
    override fun plugin(id: String): NewsSourcePlugin = byId[id] ?: error("Плагин источника недоступен: $id")
    override fun canonicalAddress(input: String, id: String) = plugin(id).canonicalAddress(input)
    override fun merge(existing: ArticleEntity, incoming: ArticleEntity): ArticleEntity =
        byId[incoming.sourcePluginId]?.merge(existing, incoming) ?: existing
    override suspend fun collect(source: SourceEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        plugin(source.kind).collect(source).map { it.copy(sourcePluginId = source.kind) }
    }
    companion object {
        fun builtIns(settings: SourceSettings? = null, telegram: TelegramProxyClient? = null) = SourcePluginRegistry(listOf(
            RssSourcePlugin { url -> NewsHttp.download(url, "fetch", settings?.workerUrl() ?: NewsRouting.DEFAULT_WORKER, 3_000_000).toString(Charsets.UTF_8) },
            TelegramPreviewSourcePlugin { url -> NewsHttp.download(url, "fetch", settings?.workerUrl() ?: NewsRouting.DEFAULT_WORKER, 3_000_000).toString(Charsets.UTF_8) },
            TelegramProxySourcePlugin(telegram ?: TelegramProxyClient { TelegramProxyConfig(settings?.telegramProxyUrl().orEmpty(), settings?.telegramProxyKey().orEmpty()) })
        ))
    }
}

class RssSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.RSS, "RSS / Atom", "HTTPS-ссылка на RSS или Atom")
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = FeedParsers().parseFeed(fetch(source.address), source)
}

class TelegramPreviewSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.TELEGRAM, "Telegram", "@канал или https://t.me/канал")
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = FeedParsers().parseTelegramPreview(fetch(source.address), source)
}

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
