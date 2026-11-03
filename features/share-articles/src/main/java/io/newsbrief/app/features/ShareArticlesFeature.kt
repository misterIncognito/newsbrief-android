package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.ShareArticleAction

class ShareArticlesFeature : AppFeature {
    override val id = "share-articles"
    override fun configure(bindings: FeatureBindings) { bindings.articleActions += ShareArticleAction() }
}
