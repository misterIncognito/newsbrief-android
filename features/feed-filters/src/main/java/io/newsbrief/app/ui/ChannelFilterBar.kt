package io.newsbrief.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
@OptIn(ExperimentalFoundationApi::class)
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
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            val longPressLabel = stringResource(R.string.channel_select_multiple)
            Surface(shape = RoundedCornerShape(8.dp),
                color = if (all) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                border = if (all) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("channel-all").defaultMinSize(minHeight = 32.dp)
                    .combinedClickable(role = Role.Button, onClick = filter::clearFeedFilter,
                        onLongClickLabel = longPressLabel, onLongClick = { menuOpen = true })) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.channel_unread, stringResource(R.string.channel_all), counts.sumOf { it.unreadCount }),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.testTag("channel-multiselect")) {
                DropdownMenuItem(text = { Text(stringResource(R.string.channel_select_multiple), style = MaterialTheme.typography.titleSmall) }, onClick = {}, enabled = false)
                options.forEach { option ->
                    val checked = option.key in selected
                    val count = option.ids.sumOf { countBySource[it] ?: 0 }
                    DropdownMenuItem(text = { Text(stringResource(R.string.channel_unread, option.label, count)) },
                        onClick = { filter.toggleFeedSource(option.key) },
                        trailingIcon = if (checked) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null,
                        modifier = Modifier.testTag("channel-menu-${option.key}")
                            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.channel_done)) }, onClick = { menuOpen = false })
            }
        }
        options.forEach { option ->
            val count = option.ids.sumOf { countBySource[it] ?: 0 }
            FilterChip(selected = option.key in selected, onClick = { filter.filterSource(option.key) },
                label = { Text(stringResource(R.string.channel_unread, option.label, count), maxLines = 1) },
                modifier = Modifier.testTag("channel-${option.key}"))
        }
    }
}
