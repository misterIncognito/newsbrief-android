package io.newsbrief.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.AiSettings

@Composable fun AiSettingsSection(vm: NewsViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val saved by vm.hasKey.collectAsStateWithLifecycle()
    var model by remember(config) { mutableStateOf(config.model) }
    var key by remember { mutableStateOf("") }
    Text("ИИ-сводки", style = MaterialTheme.typography.titleLarge)
    listOf(AiSettings.QWEN, AiSettings.OPENAI).forEach { provider ->
        FilterChip(config.provider == provider, { vm.setProvider(provider) }, label = { Text(provider) })
    }
    OutlinedTextField(model, { model = it }, label = { Text("Модель") })
    OutlinedButton(onClick = { vm.setModel(model) }) { Text("Сохранить модель") }
    Text(if (saved) "API-ключ сохранён" else "Добавь API-ключ")
    OutlinedTextField(key, { key = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("API-ключ") })
    OutlinedButton(onClick = { vm.saveKey(key); key = "" }) { Text("Сохранить ключ") }
}
