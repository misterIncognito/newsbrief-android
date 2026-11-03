package io.newsbrief.app.features

import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*

class ArticlePluginsFeature : AppFeature {
    override val id = "article-plugins"
    override fun configure(bindings: FeatureBindings) {
        bindings.extractor = { settings ->
            ArticleFetchRegistry.builtIns(bindings.telegramContent(settings), bindings.articlePlugins.map { it(settings) },
                parseArticle = { raw, url -> (bindings.parseArticle ?: error("Article parser unavailable"))(raw, url) },
                ampAlternative = { raw, url -> bindings.ampAlternative(raw, url) })
        }
    }
}
