package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.rememberLazyListState
import io.newsbrief.app.runtime.AppFeature
import io.newsbrief.app.ui.*

class FeedFeature : AppFeature {
    override val id = "feed"
    override val title = "Лента"
    override val order = -10
    @Composable override fun Screen(vm: NewsViewModel) {
        val reader by vm.readerSource.collectAsStateWithLifecycle()
        val browser by vm.browserUrl.collectAsStateWithLifecycle()
        val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
        FeedAutoRefresh(reader == null && browser == null) { vm.feedBecameVisible() }
        FeedPage(vm, rememberLazyListState(), rememberLazyListState(), controls = { vm.bindings.feedControls.forEach { it(vm) } }, emptyMessage = if (savedOnly) vm.bindings.feedEmptyMessage(vm) else null) { article ->
            val presentation = vm.presentation
            if (presentation != null) presentation.Card(article, vm) else BasicNewsCard(article, vm)
        }
    }
}
