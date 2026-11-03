package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.feed.R

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.rememberLazyListState
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class FeedFeature : AppFeature {
    override val id = "feed"
    override fun localizedTitle(context: Context): String = context.getString(R.string.nb_feature_feed)
    override val title = "Лента"
    override val order = -10
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(FeedUi::class.java, ::FeedViewModel)
        bindings.commands["refresh"] = { it.controller(FeedUi::class.java).refresh() }
    }
    @Composable override fun Screen(host: FeatureHost) {
        val vm = host.controller(FeedUi::class.java)
        val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
        FeedAutoRefresh(true) { vm.feedBecameVisible() }
        FeedPage(vm, rememberLazyListState(), rememberLazyListState(), controls = { host.bindings.feedControls.forEach { it(host) } }, emptyMessage = if (savedOnly) host.bindings.feedEmptyMessage(host) else null) { article ->
            val presentation = host.bindings.presentation
            if (presentation != null) presentation.Card(article, host) else BasicNewsCard(article, host)
        }
    }
}
