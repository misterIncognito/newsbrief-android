package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R
import io.newsbrief.modules.features.feed_filters.R as FilterR

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.FeedCategory
import java.util.Locale

private val FilterIcon = ImageVector.Builder(name = "Filter", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
        moveTo(10f, 18f); horizontalLineTo(14f); verticalLineTo(16f); horizontalLineTo(10f); close()
        moveTo(3f, 6f); verticalLineTo(8f); horizontalLineTo(21f); verticalLineTo(6f); close()
        moveTo(6f, 13f); horizontalLineTo(18f); verticalLineTo(11f); horizontalLineTo(6f); close()
    }
}.build()

/** Optional feed contribution; all selection and panel state belongs to this feature. */
@Composable
fun FeedFilterControls(vm: FeedFilterUi, saved: SavedUi? = null, openSources: (() -> Unit)? = null, searchOnline: ((String) -> Unit)? = null) {
    val category by vm.feedCategory.collectAsStateWithLifecycle()
    val categories by vm.availableCategories.collectAsStateWithLifecycle()
    val selected by vm.feedSources.collectAsStateWithLifecycle()
    val options by vm.feedOptions.collectAsStateWithLifecycle()
    val savedOnly = saved?.savedOnly?.collectAsStateWithLifecycle()?.value ?: false
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    if (savedOnly) return

    IconButton(onClick = { open = true }, modifier = Modifier.testTag("open-source-filter")) {
        BadgedBox(badge = {
            if (selected.isNotEmpty() || category != FeedCategory.ALL) Badge()
        }) { Icon(FilterIcon, contentDescription = stringResource(R.string.nb_text_009)) }
    }
    if (!open) return
    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { open = false },
            contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.88f)
                    .testTag("source-filter-panel")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp), tonalElevation = 6.dp
            ) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.nb_text_009), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { open = false }) { Text(stringResource(R.string.nb_text_010)) }
                    }
                    if (openSources != null) {
                        TextButton(onClick = { open = false; openSources() }) { Text(stringResource(R.string.nb_text_011)) }
                    }
                    Text(stringResource(R.string.nb_text_012), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { choice ->
                            FilterChip(selected = category == choice, onClick = { vm.filterCategory(choice) },
                                label = { Text(stringResource(when (choice) {
                                    FeedCategory.ALL -> R.string.nb_category_all
                                    FeedCategory.TECH -> R.string.nb_category_tech
                                    FeedCategory.BUSINESS -> R.string.nb_category_business
                                    FeedCategory.BELARUS -> R.string.nb_category_belarus
                                    FeedCategory.POLAND -> R.string.nb_category_poland
                                    FeedCategory.WORLD -> R.string.nb_category_world
                                    FeedCategory.GENERAL -> R.string.nb_category_general
                                    FeedCategory.OTHER -> R.string.nb_category_other
                                })) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        label = { Text(stringResource(R.string.nb_text_013)) }, modifier = Modifier.fillMaxWidth().testTag("source-filter-search"))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.nb_text_014), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (category != FeedCategory.ALL || selected.isNotEmpty()) {
                            TextButton(onClick = { vm.clearFeedFilter(); search = "" }) { Text(stringResource(R.string.nb_text_015)) }
                        }
                    }
                    val needle = search.trim().lowercase(Locale.ROOT)
                    val visible = options.filter { needle.isEmpty() || it.label.lowercase(Locale.ROOT).contains(needle) }
                    if (visible.isEmpty()) {
                        Text(if (needle.isEmpty() && options.isEmpty()) stringResource(R.string.nb_no_sources) else stringResource(R.string.nb_sources_not_found),
                            modifier = Modifier.padding(vertical = 16.dp))
                        if (needle.isNotEmpty() && searchOnline != null) {
                            OutlinedButton(onClick = { val query = search.trim(); open = false; searchOnline(query) },
                                modifier = Modifier.testTag("search-online-sources")) {
                                Text(stringResource(FilterR.string.search_online_sources))
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(visible, key = { it.key }) { option ->
                            val checked = option.key in selected
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    RoundedCornerShape(12.dp))
                                .toggleable(value = checked, role = Role.Checkbox,
                                    onValueChange = { vm.toggleFeedSource(option.key) })
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, modifier = Modifier.weight(1f),
                                    color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Text(if (selected.isEmpty()) stringResource(R.string.nb_all_sources) else stringResource(R.string.nb_selected_count, selected.size),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
