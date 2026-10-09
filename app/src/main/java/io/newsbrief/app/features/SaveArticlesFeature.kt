package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.SaveArticleService
import io.newsbrief.app.ui.*

class SaveArticlesFeature : AppFeature {
    override val id = "save-articles"
    override fun configure(bindings: FeatureBindings) {
        bindings.saveArticle = SaveArticleService::toggle
        bindings.articleActions += SaveArticleAction()
        bindings.feedControls += { SavedFeedControls(it) }
        bindings.feedEmptyMessage = { if (it.savedOnly.value) "Здесь будут сохранённые публикации. Нажми «Сохранить» под новостью." else null }
    }
}
