package io.newsbrief.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun TelegramSettingsSection(vm: AppUi) {
    val proxy by vm.telegramProxyUrl.collectAsStateWithLifecycle()
    val saved by vm.hasTelegramProxyKey.collectAsStateWithLifecycle()
    var url by remember(proxy) { mutableStateOf(proxy) }
    var key by remember { mutableStateOf("") }
    Text("Telegram-прокси", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("HTTPS URL Telegram-прокси") })
    Text(if (saved) "Ключ доступа сохранён" else "Ключ доступа не добавлен")
    OutlinedTextField(key, { key = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Ключ доступа Telegram-прокси") })
    OutlinedButton(onClick = { vm.saveTelegramProxy(url, key); key = "" }) { Text("Сохранить Telegram-прокси") }
    if (saved) TextButton(onClick = vm::removeTelegramProxyKey) { Text("Удалить ключ Telegram-прокси") }
}
