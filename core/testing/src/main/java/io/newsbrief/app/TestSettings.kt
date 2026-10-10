package io.newsbrief.app

import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsRouting
import io.newsbrief.app.network.ProxyUrls

/** In-memory configuration for feature tests; no storage implementation or Android Keystore. */
class TestSettings(@Suppress("UNUSED_PARAMETER") context: android.content.Context? = null) : SourceSettings, SyncSettings, AiSettings {
    private var worker = NewsRouting.DEFAULT_WORKER
    private var telegram = ""
    private var key = ""
    private var attempt: FeedSyncAttempt? = null
    override fun workerUrl() = worker
    override fun telegramProxyUrl() = telegram
    override fun telegramProxyKey() = key
    override fun feedSyncAttempt() = attempt
    override fun recordFeedSyncAttempt(time: Long, configuration: String) { attempt = FeedSyncAttempt(time, configuration) }
    override fun read() = AiConfig(AiSettings.QWEN, "test-model")
    override fun loadApiKey(provider: String) = ""
    fun setWorkerUrl(value: String) {
        io.newsbrief.app.network.ProxyUrls.workerService(value)
        worker = value.trim().trimEnd('/')
    }
    fun setTelegramProxyUrl(value: String) {
        if (value.isNotBlank()) ProxyUrls.telegramService(value)
        telegram = value
    }
    fun saveTelegramProxyKey(value: String) { key = value }
}
