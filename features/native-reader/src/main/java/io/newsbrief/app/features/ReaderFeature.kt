package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.*
import io.newsbrief.app.network.*
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.ui.*

class ReaderFeature : AppFeature, ArticlePresentation {
    override val id = "native-reader"
    override val order = 40
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(ReaderUi::class.java, ::ReaderViewModel)
        bindings.parseArticle = ArticleReader::parseArticle
        bindings.ampAlternative = AmpDiscovery::alternative
        bindings.article = { settings, extractor -> ArticleReader(settings, plugins = extractor, telegram = bindings.telegramContent(settings)) }
        bindings.images = { ArticleImageCache(java.io.File(it.filesDir, "reader-images")) }
        bindings.articleStore = { io.newsbrief.app.data.ReaderArticleStore(it) }
        bindings.navigation += NavigationHandler("article", 20) { host, article -> host.controller(ReaderUi::class.java).openReader(article) }
        bindings.presentation = this
    }
    @Composable override fun Card(article: ArticleEntity, host: FeatureHost) { NewsCard(article, host.controller(ReaderUi::class.java), host) }
}
