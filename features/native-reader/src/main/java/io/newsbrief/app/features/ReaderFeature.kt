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
        bindings.article = { settings, extractor ->
            fun fetch(url: String, type: String, worker: String, limit: Int, required: Boolean): ByteArray =
                (bindings.http ?: error("Networking plugin is unavailable"))
                    .resource(PageRequest(url, type, required), worker, limit).bytes
            ArticleReader(settings,
                proxyDownload = { url, type, worker, limit -> fetch(url, type, worker, limit, true) },
                plugins = extractor, telegram = bindings.telegramContent(settings),
                download = { url, type, worker, limit -> fetch(url, type, worker, limit, false) })
        }
        bindings.images = { context ->
            ArticleImageCache(java.io.File(context.filesDir, "reader-images"),
                proxyDownload = { url, worker, required ->
                    (bindings.http ?: error("Networking plugin is unavailable"))
                        .resource(PageRequest(url, "image", required, preferProxy = true), worker, 8_000_000).bytes
                })
        }
        bindings.articleStore = { io.newsbrief.app.data.ReaderArticleStore(it) }
        bindings.navigation += NavigationHandler("article", 20) { host, article -> host.controller(ReaderUi::class.java).openReader(article) }
        bindings.presentation = this
    }
    @Composable override fun Card(article: ArticleEntity, host: FeatureHost) { NewsCard(article, host.controller(ReaderUi::class.java), host) }
}
