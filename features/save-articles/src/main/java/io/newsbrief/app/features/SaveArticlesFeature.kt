package io.newsbrief.app.features

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.SaveArticleService
import io.newsbrief.app.ui.*

class SaveArticlesFeature : AppFeature {
    override val id = "save-articles"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(SavedUi::class.java, ::SavedArticlesViewModel)
        bindings.saveArticle = SaveArticleService::toggle
        bindings.articleActions += SaveArticleAction()
        bindings.feedEmptyMessage = { if (it.controller(SavedUi::class.java).savedOnly.value) it.context.getString(R.string.nb_saved_empty) else null }
    }
    override fun onFeedSelected(host: FeatureHost) { host.controller(SavedUi::class.java).showSaved(false) }
    @Composable override fun HeaderActions(host: FeatureHost, openFeed: () -> Unit) {
        val saved = host.controller(SavedUi::class.java)
        val selected by saved.savedOnly.collectAsStateWithLifecycle()
        IconToggleButton(checked = selected, onCheckedChange = {
            saved.showSaved(it)
            openFeed()
        }, modifier = Modifier.testTag("open-saved")) {
            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.nb_text_078))
        }
    }
}
