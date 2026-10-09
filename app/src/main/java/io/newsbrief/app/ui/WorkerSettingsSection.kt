package io.newsbrief.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun WorkerSettingsSection(vm: NewsViewModel) {
    val worker by vm.workerUrl.collectAsStateWithLifecycle()
    var value by remember(worker) { mutableStateOf(worker) }
    Text("Прокси для новостей", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("HTTPS URL Cloudflare Worker") })
    OutlinedButton(onClick = { vm.setWorkerUrl(value) }) { Text("Сохранить прокси") }
}
