package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.network.*

@Composable
fun SourceDirectoryPage(vm: DirectoryUi) {
    val state by vm.directoryState.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val existing = sources.map { it.address.lowercase(java.util.Locale.ROOT) }.toSet()
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.nb_text_037), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectoryProvider.entries.forEach { provider ->
                FilterChip(selected = provider == state.provider, onClick = { vm.directoryProvider(provider) },
                    label = { Text(provider.label) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.query, onValueChange = { vm.directoryQuery(it) }, singleLine = true,
                label = { Text(stringResource(R.string.nb_text_038)) }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchDirectory() }))
            Button(onClick = { vm.searchDirectory() }, enabled = !state.busy,
                modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.nb_text_039)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.nb_directory_tech) to "technology", stringResource(R.string.nb_directory_ai) to "artificial intelligence", stringResource(R.string.nb_directory_code) to "programming").forEach { (label, query) ->
                TextButton(onClick = { vm.directoryQuery(query); vm.searchDirectory() }) { Text(label) }
            }
        }
        val page = state.results
        val provider = when (state.provider) {
            DirectoryProvider.RSS -> stringResource(R.string.nb_directory_rss)
            DirectoryProvider.TELEGRAM -> stringResource(R.string.nb_directory_telegram)
            DirectoryProvider.CURATED -> stringResource(R.string.nb_directory_curated)
        }
        Text(provider, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        state.addMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
        page?.let {
            val displayed = if (it.category == "ai") it.items.filter { source -> TelegramTopics.show(source, state.hidePromotions) } else it.items
            if (it.category == "ai") Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)) {
                Checkbox(checked = state.hidePromotions, onCheckedChange = vm::hideDirectoryPromotions)
                Text(stringResource(R.string.nb_text_046), style = MaterialTheme.typography.bodySmall)
            }
            val status = if (it.total != null) {
                val countLabel = if (it.category != null) stringResource(R.string.nb_in_category) else stringResource(R.string.nb_found)
                stringResource(R.string.nb_count_displayed, countLabel, it.total ?: 0, displayed.size)
            } else stringResource(R.string.nb_displayed_count, displayed.size)
            Text("${state.searchedQuery} · $status" + if (it.cached) stringResource(R.string.nb_cached_suffix) else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            it.notice?.let { notice -> Text(notice, modifier = Modifier.padding(horizontal = 18.dp)) }
            if (displayed.isEmpty()) Text(if (it.items.isNotEmpty()) stringResource(R.string.nb_empty_page) else stringResource(R.string.nb_no_results), modifier = Modifier.padding(18.dp))
            if (it.category == null && it.total != null && (it.total ?: 0) > it.items.size) Text(stringResource(R.string.nb_text_051),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(displayed, key = { source -> source.address }) { source ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(source.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                val added = source.address.lowercase(java.util.Locale.ROOT) in existing
                                val supported = vm.sourcePlugins.any { it.id == source.kind }
                                TextButton(enabled = supported && !added && state.addingAddress == null,
                                    onClick = { vm.addDirectorySource(source) }) {
                                    Text(if (added) stringResource(R.string.nb_added) else if (state.addingAddress == source.address) stringResource(R.string.nb_checking) else stringResource(R.string.nb_add))
                                }
                            }
                            if (source.description.isNotBlank()) Text(source.description, maxLines = 3,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(listOfNotNull(source.kind, source.language.takeIf { it.isNotBlank() },
                                source.audience?.let { count -> stringResource(R.string.nb_audience, count) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall)
                            Text(source.address, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (it.nextOffset != null) OutlinedButton(onClick = vm::loadMoreDirectory,
                enabled = !state.busy && state.query.trim() == state.searchedQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(if (state.busy) stringResource(R.string.nb_loading) else stringResource(R.string.nb_load_more))
            }
        }
    }
}
