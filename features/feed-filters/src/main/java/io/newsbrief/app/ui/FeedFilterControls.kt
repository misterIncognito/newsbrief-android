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

@Composable fun FeedFilterControls(vm: FeedFilterUi, saved: SavedUi? = null) {
    val category by vm.feedCategory.collectAsStateWithLifecycle()
    val selectedSources by vm.feedSources.collectAsStateWithLifecycle()
    val options by vm.feedOptions.collectAsStateWithLifecycle()
    val savedOnly by (saved?.savedOnly ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsStateWithLifecycle()
    val filtered = category != FeedCategory.ALL || selectedSources.isNotEmpty()
        if (!savedOnly) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedCategory.entries.forEach { choice ->
                    FilterChip(selected = category == choice, onClick = { vm.filterCategory(choice) },
                        label = { Text(choice.label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedSources.isEmpty(), onClick = { vm.filterSource(null) },
                        label = { Text("Все источники") })
                    options.forEach { option ->
                        FilterChip(selected = option.key in selectedSources, onClick = { vm.toggleFeedSource(option.key) },
                            label = { Text(option.label) })
                    }
                }
                if (filtered) TextButton(onClick = { vm.clearFeedFilter() }) { Text("Сбросить") }
            }
        }

}
