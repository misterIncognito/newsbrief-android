package io.newsbrief.app.ui

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
fun SourceDirectoryPage(vm: NewsViewModel) {
    val state by vm.directoryState.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val existing = sources.map { it.address.lowercase(java.util.Locale.ROOT) }.toSet()
    Column(Modifier.fillMaxSize()) {
        Text("Найти новые источники", style = MaterialTheme.typography.headlineSmall,
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
                label = { Text("Тема, название или адрес") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchDirectory() }))
            Button(onClick = { vm.searchDirectory() }, enabled = !state.busy,
                modifier = Modifier.padding(top = 8.dp)) { Text("Найти") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Технологии" to "technology", "ИИ" to "artificial intelligence", "Код" to "programming",
                "Новости" to "news", "Бизнес" to "business").forEach { (label, query) ->
                TextButton(onClick = { vm.directoryQuery(query); vm.searchDirectory() }) { Text(label) }
            }
        }
        val page = state.results
        val provider = when (state.provider) {
            DirectoryProvider.RSS -> "Поиск по внешнему индексу RSS · Feedly"
            DirectoryProvider.TELEGRAM -> page?.directorySize?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} каналов в каталоге · TGX" }
                ?: "Поиск по внешнему каталогу Telegram · TGX"
            DirectoryProvider.CURATED -> "Наша небольшая подборка проверенных адресов"
        }
        Text(provider, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        state.addMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
        page?.let {
            val status = if (it.total != null) "Найдено: ${it.total} · показано: ${it.items.size}" else "Показано: ${it.items.size}"
            Text("${state.searchedQuery} · $status" + if (it.cached) " · сохранённые результаты" else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            it.notice?.let { notice -> Text(notice, modifier = Modifier.padding(horizontal = 18.dp)) }
            if (it.items.isEmpty()) Text("Ничего не найдено. Попробуй другое название или тему.", modifier = Modifier.padding(18.dp))
            if (it.total != null && it.total > it.items.size) Text("Каталог показывает наиболее подходящие результаты. Уточни запрос, чтобы найти другие каналы.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(it.items, key = { source -> source.address }) { source ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(source.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                val added = source.address.lowercase(java.util.Locale.ROOT) in existing
                                TextButton(enabled = !added && state.addingAddress == null,
                                    onClick = { vm.addDirectorySource(source) }) {
                                    Text(if (added) "Добавлен" else if (state.addingAddress == source.address) "Проверка…" else "Добавить")
                                }
                            }
                            if (source.description.isNotBlank()) Text(source.description, maxLines = 3,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(listOfNotNull(source.kind, source.language.takeIf { it.isNotBlank() },
                                source.audience?.let { count -> "Аудитория: $count" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall)
                            Text(source.address, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
