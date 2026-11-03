package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.SaveArticleService
import io.newsbrief.app.ui.*

class SaveArticlesFeature : AppFeature {
    override val id = "save-articles"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(SavedUi::class.java, ::SavedArticlesViewModel)
        bindings.saveArticle = SaveArticleService::toggle
        bindings.articleActions += SaveArticleAction()
        bindings.feedEmptyMessage = { if (it.controller(SavedUi::class.java).savedOnly.value) "Здесь будут сохранённые публикации. Нажми «Сохранить» под новостью." else null }
    }
    override fun onFeedSelected(host: FeatureHost) { host.controller(SavedUi::class.java).showSaved(false) }
    @Composable override fun MenuItems(host: FeatureHost, openFeed: () -> Unit, closeMenu: () -> Unit) {
        DropdownMenuItem(text = { Text("Сохранённые") }, onClick = {
            host.controller(SavedUi::class.java).showSaved(true)
            openFeed()
            closeMenu()
        })
    }
}
