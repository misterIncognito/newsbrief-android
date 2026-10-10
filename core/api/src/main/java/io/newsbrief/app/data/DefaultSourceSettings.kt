package io.newsbrief.app.data

/** Read-only defaults for plugin factories used without a configured host. */
object DefaultSourceSettings : SourceSettings {
    override fun workerUrl() = io.newsbrief.app.network.NewsRouting.DEFAULT_WORKER
    override fun telegramProxyUrl() = ""
    override fun telegramProxyKey() = ""
}
