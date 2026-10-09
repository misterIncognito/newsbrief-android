package io.newsbrief.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.SettingsStore

private enum class Page(val label: String, val glyph: String) {
    DIGEST("Главное", "✦"), NEWS("Лента", "≡"), SOURCES("Источники", "+"), SETTINGS("Настройки", "⚙")
}

@Composable
fun NewsBriefScreen(vm: NewsViewModel) {
    var page by rememberSaveable { mutableStateOf(Page.DIGEST) }
    val readerSource by vm.readerSource.collectAsStateWithLifecycle()
    val browserUrl by vm.browserUrl.collectAsStateWithLifecycle()
    val workerUrl by vm.workerUrl.collectAsStateWithLifecycle()
    val browserForceProxy by vm.browserForceProxy.collectAsStateWithLifecycle()
    val catalogOpen by vm.catalogOpen.collectAsStateWithLifecycle()
    val feedScroll = rememberLazyListState()
    val savedScroll = rememberLazyListState()
    FeedAutoRefresh(page == Page.NEWS && readerSource == null && browserUrl == null && !catalogOpen) {
        vm.feedBecameVisible()
    }
    BackHandler(enabled = readerSource != null || browserUrl != null || catalogOpen) {
        if (browserUrl != null) vm.closeBrowser()
        else if (readerSource != null) vm.closeReader() else vm.closeCatalog()
    }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        vm.clearMessage()
        snackbar.showSnackbar(text)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("NewsBrief", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Новости без информационного шума", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (browserUrl != null) {
                    TextButton(onClick = { vm.closeBrowser() }) { Text("← К статье") }
                } else if (catalogOpen) {
                    TextButton(onClick = { vm.closeCatalog() }) { Text("← Назад") }
                } else if (readerSource != null) {
                    TextButton(onClick = { vm.closeReader() }) { Text("← Назад") }
                } else {
                    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
                    TextButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                        Text(if (refreshing) "Загрузка…" else "Обновить")
                    }
                }
            }
        },
        bottomBar = {
            if (readerSource == null && browserUrl == null && !catalogOpen) NavigationBar {
                Page.entries.forEach { candidate ->
                    NavigationBarItem(
                        selected = page == candidate,
                        onClick = { page = candidate },
                        icon = { Text(candidate.glyph) },
                        label = { Text(candidate.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (browserUrl != null) {
                EmbeddedBrowserPage(browserUrl!!, workerUrl, browserForceProxy)
            } else if (readerSource != null) {
                ReaderPage(vm)
            } else if (catalogOpen) {
                SourceDirectoryPage(vm)
            } else when (page) {
                Page.DIGEST -> DigestPage(vm)
                Page.NEWS -> FeedPage(vm, feedScroll, savedScroll) { NewsCard(it, vm) }
                Page.SOURCES -> SourcesPage(vm)
                Page.SETTINGS -> SettingsPage(vm)
            }
        }
    }
}















@Composable
private fun SettingsPage(vm: NewsViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var modelDraft by remember(config.provider) { mutableStateOf(config.model) }
    var keyDraft by remember(config.provider) { mutableStateOf("") }
    val worker by vm.workerUrl.collectAsStateWithLifecycle()
    var workerDraft by remember(worker) { mutableStateOf(worker) }
    val telegramProxy by vm.telegramProxyUrl.collectAsStateWithLifecycle()
    val telegramKeySaved by vm.hasTelegramProxyKey.collectAsStateWithLifecycle()
    var telegramUrlDraft by remember(telegramProxy) { mutableStateOf(telegramProxy) }
    var telegramKeyDraft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Прокси для новостей", style = MaterialTheme.typography.headlineSmall)
        Text("Для «Зеркала» и «Медузы» RSS, статьи и изображения загружаются через Cloudflare Worker. При ошибке соединение напрямую не используется.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = workerDraft, onValueChange = { workerDraft = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("HTTPS URL Cloudflare Worker") })
        OutlinedButton(onClick = { vm.setWorkerUrl(workerDraft) }) { Text("Сохранить прокси") }
        HorizontalDivider()
        Text("Telegram-прокси", style = MaterialTheme.typography.headlineSmall)
        Text("Укажи адрес отдельного сервиса Telegram и свой ключ доступа. Затем выбери его каналы в разделе «Источники». Ключ хранится зашифрованным на устройстве.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = telegramUrlDraft, onValueChange = { telegramUrlDraft = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS URL Telegram-прокси") })
        Text(if (telegramKeySaved) "Ключ доступа сохранён" else "Ключ доступа не добавлен")
        OutlinedTextField(value = telegramKeyDraft, onValueChange = { telegramKeyDraft = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), label = { Text("Ключ доступа Telegram-прокси") })
        OutlinedButton(onClick = { vm.saveTelegramProxy(telegramUrlDraft, telegramKeyDraft); telegramKeyDraft = "" }) { Text("Сохранить Telegram-прокси") }
        if (telegramKeySaved) TextButton(onClick = { vm.removeTelegramProxyKey() }) { Text("Удалить ключ Telegram-прокси") }
        HorizontalDivider()
        Text("Настройки ИИ", style = MaterialTheme.typography.headlineSmall)
        Text("Выбери модель. Выдержки публикаций отправляются напрямую с телефона выбранному ИИ-провайдеру, без нашего сервера.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = config.provider == SettingsStore.QWEN,
                onClick = { vm.setProvider(SettingsStore.QWEN) }, label = { Text("Qwen") })
            FilterChip(selected = config.provider == SettingsStore.OPENAI,
                onClick = { vm.setProvider(SettingsStore.OPENAI) }, label = { Text("OpenAI") })
        }
        OutlinedTextField(value = modelDraft, onValueChange = { modelDraft = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Модель") })
        OutlinedButton(onClick = { vm.setModel(modelDraft) }) { Text("Сохранить модель") }
        HorizontalDivider()
        Text(if (hasKey) "API-ключ сохранён" else "API-ключ ещё не добавлен", style = MaterialTheme.typography.titleMedium)
        Text("Ключ хранится в зашифрованном виде с Android Keystore. Это подходит для личного ключа, но не для общего ключа, встроенного в APK.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = keyDraft, onValueChange = { keyDraft = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            label = { Text("API-ключ ${config.provider}") })
        Button(onClick = { vm.saveKey(keyDraft); keyDraft = "" }, enabled = keyDraft.isNotBlank()) {
            Text("Сохранить ключ")
        }
        if (hasKey) TextButton(onClick = { vm.saveKey("") }) { Text("Удалить сохранённый ключ") }
        HorizontalDivider()
        Text("Ограничения MVP", style = MaterialTheme.typography.titleMedium)
        Text("В сводку передаются заголовки и доступные выдержки, а не полные статьи. Публичная Telegram-страница показывает ограниченное число последних сообщений. Фоновое обновление Android выполняет с возможными задержками.",
            style = MaterialTheme.typography.bodyMedium)
    }
}
