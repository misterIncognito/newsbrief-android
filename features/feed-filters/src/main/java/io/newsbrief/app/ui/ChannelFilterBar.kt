package io.newsbrief.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleDao
import io.newsbrief.app.data.FeedCategory
import io.newsbrief.modules.features.feed_filters.R

/** Quick access to the same source selection used by the filter panel. */
@Composable
fun ChannelFilterBar(filter: FeedFilterUi, articles: ArticleDao, saved: SavedUi? = null) {
    val savedOnly = saved?.savedOnly?.collectAsStateWithLifecycle()?.value ?: false
    if (savedOnly) return
    val options by filter.feedOptions.collectAsStateWithLifecycle()
    val selected by filter.feedSources.collectAsStateWithLifecycle()
    val category by filter.feedCategory.collectAsStateWithLifecycle()
    val counts by androidx.compose.runtime.remember(articles) { articles.observeUnreadCountsBySource() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val countBySource = counts.associate { it.sourceId to it.unreadCount }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)
        .testTag("channel-filter-bar"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val all = category == FeedCategory.ALL && selected.isEmpty()
        FilterChip(selected = all, onClick = filter::clearFeedFilter,
            label = { Text(stringResource(R.string.channel_unread, stringResource(R.string.channel_all), counts.sumOf { it.unreadCount })) },
            modifier = Modifier.testTag("channel-all"))
        options.forEach { option ->
            val count = option.ids.sumOf { countBySource[it] ?: 0 }
            FilterChip(selected = option.key in selected, onClick = { filter.filterSource(option.key) },
                label = { Text(stringResource(R.string.channel_unread, option.label, count), maxLines = 1) },
                modifier = Modifier.testTag("channel-${option.key}"))
        }
    }
}
