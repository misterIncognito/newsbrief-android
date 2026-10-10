package io.newsbrief.app.data

interface WorkerSettings { fun workerUrl(): String }
interface SourceSettings : WorkerSettings {
    fun telegramProxyUrl(): String
    fun telegramProxyKey(): String
}

data class FeedSyncAttempt(val time: Long, val configuration: String)
interface SyncSettings : WorkerSettings {
    fun telegramProxyUrl(): String
    fun feedSyncAttempt(): FeedSyncAttempt?
    fun recordFeedSyncAttempt(time: Long, configuration: String)
}

data class AiConfig(val provider: String, val model: String)
interface AiSettings {
    fun read(): AiConfig
    fun loadApiKey(provider: String): String
    companion object {
        const val QWEN = "Qwen"
        const val OPENAI = "OpenAI"
    }
}
