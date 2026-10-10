package io.newsbrief.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.SourceKind


@Composable fun TelegramProxyChannelsPage(vm: SourcesUi) {
    val state by vm.proxyChannels.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.openProxyChannels() }
    if (state.open) ProxyChannelsDialog(vm, state)
    else Column(Modifier.padding(16.dp)) {
        Text("Telegram-прокси")
        OutlinedButton(onClick = vm::openProxyChannels) { Text("Каналы Telegram-прокси") }
    }
}
@Composable
private fun ProxyChannelsDialog(vm: SourcesUi, state: ProxyChannelsState) {
    var search by rememberSaveable { mutableStateOf("") }
    val sources by vm.sources.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = { vm.closeProxyChannels() }, title = { Text("Каналы Telegram-прокси") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Доступные каналы, которые уже собирает сервис. Подключение добавляет их в ленту.")
                OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true, label = { Text("Поиск канала") })
                if (state.busy) CircularProgressIndicator()
                state.error?.let { Text(it) }
                if (!state.busy && state.error == null && state.channels.isEmpty()) Text("На сервисе пока нет доступных каналов")
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(state.channels.filter { it.name.contains(search, true) || it.address.contains(search, true) }, key = { it.address }) { source ->
                        val added = sources.any { it.address == source.address }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(source.name, Modifier.weight(1f))
                            TextButton(onClick = { vm.addSource(source.name, source.kind, source.address) }, enabled = !added) {
                                Text(if (added) "Подключён" else "Добавить")
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.closeProxyChannels() }) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = { vm.openProxyChannels() }) { Text("Обновить") } })
}
