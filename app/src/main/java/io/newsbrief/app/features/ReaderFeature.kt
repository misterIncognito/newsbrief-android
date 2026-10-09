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
        bindings.article = { settings, extractor -> ArticleReader(settings, plugins = extractor, telegram = bindings.telegramContent(settings)) }
        bindings.images = { ArticleImageCache(java.io.File(it.filesDir, "reader-images")) }
        bindings.presentation = this
    }
    @Composable override fun Card(article: ArticleEntity, vm: NewsViewModel) { NewsCard(article, vm) }
    @Composable override fun Reader(vm: NewsViewModel) { ReaderPage(vm) }
}
