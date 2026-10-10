package io.newsbrief.app.data

import io.newsbrief.app.network.NewsRouting
import io.newsbrief.app.network.ProxyUrls

/** Session-only settings. Persistent credentials are supplied only by the storage feature. */
class MemorySettings : AppSettings {
    private var worker = NewsRouting.DEFAULT_WORKER
    private var telegram = ""
    private var provider = AiSettings.QWEN
    private val models = mutableMapOf<String, String>()
    private val keys = mutableMapOf<String, String>()
    private val seeded = mutableSetOf<Int>()
    private var attempt: FeedSyncAttempt? = null
    override fun read() = AiConfig(provider, models[provider] ?: if (provider == AiSettings.QWEN) "qwen3.5-flash" else "gpt-5.4-mini")
    override fun setProvider(provider: String) { require(provider in setOf(AiSettings.QWEN, AiSettings.OPENAI)); this.provider = provider }
    override fun setModel(provider: String, model: String) { require(model.isNotBlank()); models[provider] = model.trim() }
    override fun saveApiKey(provider: String, value: String) { keys[provider] = value.trim() }
    override fun hasApiKey(provider: String) = keys[provider].orEmpty().isNotBlank()
    override fun loadApiKey(provider: String) = keys[provider].orEmpty()
    override fun workerUrl() = worker
    override fun setWorkerUrl(url: String) { ProxyUrls.workerService(url); worker = url.trim().trimEnd('/') }
    override fun feedSyncAttempt() = attempt
    override fun recordFeedSyncAttempt(time: Long, configuration: String) { attempt = FeedSyncAttempt(time, configuration) }
    override fun telegramProxyUrl() = telegram
    override fun hasTelegramProxyKey() = hasApiKey("TelegramProxy")
    override fun telegramProxyKey() = loadApiKey("TelegramProxy")
    override fun saveTelegramProxyKey(value: String) = saveApiKey("TelegramProxy", value)
    override fun setTelegramProxyUrl(value: String) {
        val url = value.trim().trimEnd('/')
        if (url.isNotBlank()) ProxyUrls.telegramService(url)
        if (url != telegram) saveTelegramProxyKey("")
        telegram = url
    }
    override fun isSourcesCatalogV2Seeded() = 2 in seeded
    override fun markSourcesCatalogV2Seeded() { seeded += 2 }
    override fun isSourcesCatalogV3Seeded() = 3 in seeded
    override fun markSourcesCatalogV3Seeded() { seeded += 3 }
    override fun isSourcesCatalogV4Seeded() = 4 in seeded
    override fun markSourcesCatalogV4Seeded() { seeded += 4 }
    override fun isSourcesCatalogV5Seeded() = 5 in seeded
    override fun markSourcesCatalogV5Seeded() { seeded += 5 }
}
