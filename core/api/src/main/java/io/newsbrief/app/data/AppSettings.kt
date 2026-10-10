package io.newsbrief.app.data

interface AppSettings : SourceSettings, SyncSettings, AiSettings {
    override fun read(): AiConfig
    fun setProvider(provider: String): Unit
    fun setModel(provider: String, model: String): Unit
    fun saveApiKey(provider: String, value: String): Unit
    fun hasApiKey(provider: String): Boolean
    override fun loadApiKey(provider: String): String
    override fun workerUrl(): String
    fun setWorkerUrl(url: String): Unit
    override fun feedSyncAttempt(): FeedSyncAttempt?
    override fun recordFeedSyncAttempt(time: Long, configuration: String): Unit
    override fun telegramProxyUrl(): String
    fun hasTelegramProxyKey(): Boolean
    override fun telegramProxyKey(): String
    fun saveTelegramProxyKey(value: String): Unit
    fun setTelegramProxyUrl(value: String): Unit
    fun isSourcesCatalogV2Seeded(): Boolean
    fun markSourcesCatalogV2Seeded(): Unit
    fun isSourcesCatalogV3Seeded(): Boolean
    fun markSourcesCatalogV3Seeded(): Unit
    fun isSourcesCatalogV4Seeded(): Boolean
    fun markSourcesCatalogV4Seeded(): Unit
    fun isSourcesCatalogV5Seeded(): Boolean
    fun markSourcesCatalogV5Seeded(): Unit
}
