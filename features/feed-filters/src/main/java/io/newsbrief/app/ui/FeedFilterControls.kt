package io.newsbrief.app.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.FeedCategory
import java.util.Locale

/** Optional feed contribution; all selection and panel state belongs to this feature. */
@Composable
fun FeedFilterControls(vm: FeedFilterUi, saved: SavedUi? = null) {
    val category by vm.feedCategory.collectAsStateWithLifecycle()
    val categories by vm.availableCategories.collectAsStateWithLifecycle()
    val selected by vm.feedSources.collectAsStateWithLifecycle()
    val options by vm.feedOptions.collectAsStateWithLifecycle()
    val savedOnly = saved?.savedOnly?.collectAsStateWithLifecycle()?.value ?: false
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    if (savedOnly) return

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Start) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("open-source-filter")) {
            val active = if (selected.isNotEmpty()) " (${selected.size})" else if (category != FeedCategory.ALL) " · ${category.label}" else ""
            Text("Фильтр источников$active")
        }
    }
    if (!open) return
    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { open = false },
            contentAlignment = Alignment.CenterStart) {
            Surface(
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.88f)
                    .testTag("source-filter-panel")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), tonalElevation = 6.dp
            ) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Фильтр ленты", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { open = false }) { Text("Готово") }
                    }
                    Text("Категория", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { choice ->
                            FilterChip(selected = category == choice, onClick = { vm.filterCategory(choice) },
                                label = { Text(choice.label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        label = { Text("Найти источник") }, modifier = Modifier.fillMaxWidth().testTag("source-filter-search"))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Источники", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (category != FeedCategory.ALL || selected.isNotEmpty()) {
                            TextButton(onClick = { vm.clearFeedFilter(); search = "" }) { Text("Сбросить") }
                        }
                    }
                    val needle = search.trim().lowercase(Locale.ROOT)
                    val visible = options.filter { needle.isEmpty() || it.label.lowercase(Locale.ROOT).contains(needle) }
                    if (visible.isEmpty()) {
                        Text(if (options.isEmpty()) "Нет подключённых источников" else "Источники не найдены",
                            modifier = Modifier.padding(vertical = 16.dp))
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
                    Text(if (selected.isEmpty()) "Показаны все источники" else "Выбрано: ${selected.size}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
