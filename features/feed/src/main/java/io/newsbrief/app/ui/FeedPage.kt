package io.newsbrief.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.FeedCategory

@Composable
fun FeedPage(vm: FeedUi, feedScroll: LazyListState, savedScroll: LazyListState, controls: @Composable () -> Unit = {}, emptyMessage: String? = null, articleContent: @Composable (ArticleEntity) -> Unit) {
    val articles by vm.feedArticles.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val category by vm.feedCategory.collectAsStateWithLifecycle()
    val selectedSources by vm.feedSources.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
    val options by vm.feedOptions.collectAsStateWithLifecycle()
    val filtered = category != FeedCategory.ALL || selectedSources.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        controls()
        Text("Публикаций: ${articles.size}", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeedRefreshContainer(isRefreshing = refreshing, onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("news-feed"), state = if (savedOnly) savedScroll else feedScroll,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (articles.isEmpty()) item {
                    Text(emptyMessage ?: if (refreshing) "Загружаем новости…"
                        else if (filtered) "В этом фильтре пока нет публикаций. Выбери другой источник или потяни ленту вниз."
                        else "Потяни ленту вниз, чтобы загрузить новости.",
                        modifier = Modifier.fillParentMaxHeight().padding(22.dp))
                }
                items(articles, key = { it.id }) { article -> articleContent(article) }
            }
        }
    }
}
