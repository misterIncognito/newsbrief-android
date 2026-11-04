package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.feed.R

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.newsbrief.modules.core.api.R as CoreR
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
    @Composable override fun HeaderSummary(host: FeatureHost) {
        val articles by host.controller(FeedUi::class.java).feedArticles.collectAsStateWithLifecycle()
        val description = stringResource(CoreR.string.nb_count_articles, articles.size)
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("article-count")
            .semantics { contentDescription = description }, contentAlignment = Alignment.CenterEnd) {
            val label = if (maxWidth < 80.dp) articles.size.toString()
                else stringResource(R.string.nb_count_short, articles.size)
            Text(label, maxLines = 1, overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    @Composable override fun Screen(host: FeatureHost) {
        val vm = host.controller(FeedUi::class.java)
        val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
        val readStatus = host.optional(ReadStatusUi::class.java)
        FeedAutoRefresh(true) { vm.feedBecameVisible() }
        FeedPage(vm, rememberLazyListState(), rememberLazyListState(), controls = { host.bindings.feedControls.forEach { it(host) } }, emptyMessage = if (savedOnly) host.bindings.feedEmptyMessage(host) else null,
            onVisible = readStatus?.let { status -> { ids -> status.markVisible(ids) } },
            onVisibilityChanged = readStatus?.let { status -> { ids -> status.retainVisible(ids) } }) { article ->
            val presentation = host.bindings.presentation
            val card: @Composable () -> Unit = {
                if (presentation != null) presentation.Card(article, host) else BasicNewsCard(article, host)
            }
            if (readStatus != null) ReadStatusFrame(article, { readStatus.toggleRead(it) }, card) else card()
        }
    }
}
