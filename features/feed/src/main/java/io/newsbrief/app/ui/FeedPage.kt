package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import io.newsbrief.modules.features.feed.R as FeedR
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.FeedCategory

@Composable
fun FeedPage(vm: FeedUi, feedScroll: LazyListState, savedScroll: LazyListState, feedIdentity: Any? = null, controls: @Composable () -> Unit = {}, emptyMessage: String? = null, onVisible: (suspend (List<Long>) -> Unit)? = null, onVisibilityChanged: ((List<Long>) -> Unit)? = null, articleContent: @Composable (ArticleEntity) -> Unit) {
    val articles by vm.feedArticles.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val feedLimit by vm.feedLimit.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val activeScroll = if (savedOnly) savedScroll else feedScroll
    val headers = remember { mutableStateMapOf<Long, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var previousFeed by remember { mutableStateOf(feedIdentity) }
    LaunchedEffect(feedIdentity, activeScroll) {
        if (previousFeed != feedIdentity) {
            previousFeed = feedIdentity
            activeScroll.scrollToItem(0)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val visibleCallback by rememberUpdatedState(onVisible)
    val visibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(activeScroll, lifecycle, onVisible != null, focused) {
        if (onVisible == null || !focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                val tracker = VisibilityReadTracker(this, { visibilityChanged?.invoke(it) }) { visibleCallback?.invoke(it) }
                try {
                    snapshotFlow {
                        val layout = activeScroll.layoutInfo
                        val visible = layout.visibleItemsInfo.filter { item ->
                            item.offset < layout.viewportEndOffset && item.offset + item.size > layout.viewportStartOffset
                        }.mapNotNull { it.key as? Long }.toSet()
                        val eligible = visible.filter { id -> headers[id]?.let { isHeaderVisible(it, viewport) } == true }.toSet()
                        visible to eligible
                    }.collect { (visible, eligible) -> tracker.update(visible, eligible) }
                } finally {
                    tracker.close()
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        controls()
        if (searchOpen) {
            OutlinedTextField(value = searchQuery, onValueChange = vm::searchNews, singleLine = true,
                label = { Text(stringResource(FeedR.string.search_loaded_news)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("news-search"))
        }
        if (searchOpen && searchQuery.isNotBlank()) Text(stringResource(FeedR.string.search_all_notice),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        FeedRefreshContainer(isRefreshing = refreshing, onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("news-feed").onGloballyPositioned { coordinates ->
                val p = coordinates.positionInWindow()
                viewport = Rect(p.x, p.y, p.x + coordinates.size.width, p.y + coordinates.size.height)
            }, state = activeScroll,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (articles.isEmpty()) item {
                    Text(if (searchQuery.isNotBlank()) stringResource(FeedR.string.search_no_results) else emptyMessage ?: if (refreshing) stringResource(R.string.nb_load_news)
                        else if (filtered) stringResource(R.string.nb_empty_filter)
                        else stringResource(R.string.nb_pull_to_refresh),
                        modifier = Modifier.fillParentMaxHeight().padding(22.dp))
                }
                items(articles, key = { it.id }) { article ->
                    DisposableEffect(article.id) { onDispose { headers.remove(article.id) } }
                    val headerModifier = Modifier.onGloballyPositioned { coordinates ->
                        val p = coordinates.positionInWindow()
                        headers[article.id] = Rect(p.x, p.y, p.x + coordinates.size.width, p.y + coordinates.size.height)
                    }
                    CompositionLocalProvider(LocalFeedHeaderModifier provides headerModifier) { articleContent(article) }
                }
                if (!savedOnly && searchQuery.isBlank() && articles.size >= feedLimit) item {
                    TextButton(onClick = vm::loadMoreFeed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(FeedR.string.feed_load_more)) }
                }
            }
        }
    }
}

/** Require at least half of the actual source/date header to be visible in the feed viewport. */
internal fun isHeaderVisible(header: Rect, viewport: Rect): Boolean {
    if (header.width <= 0f || header.height <= 0f || viewport.width <= 0f || viewport.height <= 0f) return false
    val width = (minOf(header.right, viewport.right) - maxOf(header.left, viewport.left)).coerceAtLeast(0f)
    val height = (minOf(header.bottom, viewport.bottom) - maxOf(header.top, viewport.top)).coerceAtLeast(0f)
    return width * height >= header.width * header.height / 2f
}
