package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.network.*

class ArticlePluginsFeature : AppFeature {
    override val id = "article-plugins"
    override val order = 50
    override fun configure(bindings: FeatureBindings) {
        bindings.extractor = { settings -> ArticleFetchRegistry.builtIns(TelegramProxyClient {
            TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }) }
    }
}
