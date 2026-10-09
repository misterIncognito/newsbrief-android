package io.newsbrief.app.features

import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*

class TelegramSourceFeature : AppFeature {
    override val id = "telegram-source"
    override fun configure(bindings: FeatureBindings) {
        bindings.sourcePlugins += { settings -> TelegramPreviewSourcePlugin { url ->
            NewsHttp.download(url, "fetch", settings.workerUrl(), 3_000_000).toString(Charsets.UTF_8)
        } }
        bindings.telegramPlugins += { TelegramArticleContent(it) }
        bindings.sourceDefaults += io.newsbrief.app.data.SourceEntity(name = "BBC Russian · Telegram", address = "https://t.me/s/bbcrussian", kind = io.newsbrief.app.data.SourceKind.TELEGRAM)
    }
}
