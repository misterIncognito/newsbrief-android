package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.SaveArticleService
import io.newsbrief.app.ui.*

class SaveArticlesFeature : AppFeature {
    override val id = "save-articles"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(SavedUi::class.java, ::SavedArticlesViewModel)
        bindings.saveArticle = SaveArticleService::toggle
        bindings.articleActions += SaveArticleAction()
        bindings.feedControls += { SavedFeedControls(it.controller(SavedUi::class.java)) }
        bindings.feedEmptyMessage = { if (it.controller(SavedUi::class.java).savedOnly.value) "Здесь будут сохранённые публикации. Нажми «Сохранить» под новостью." else null }
    }
}
