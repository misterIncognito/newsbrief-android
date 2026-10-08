package io.newsbrief.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.DigestEntity
import io.newsbrief.app.data.DigestMode
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.data.SourceKind
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Page(val label: String, val glyph: String) {
    DIGEST("Главное", "✦"), NEWS("Лента", "≡"), SOURCES("Источники", "+"), SETTINGS("Настройки", "⚙")
}

@Composable
fun NewsBriefScreen(vm: NewsViewModel) {
    var page by rememberSaveable { mutableStateOf(Page.DIGEST) }
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
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("NewsBrief", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Новости без информационного шума", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val refreshing by vm.refreshing.collectAsStateWithLifecycle()
                TextButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                    Text(if (refreshing) "Загрузка…" else "Обновить")
                }
            }
        },
        bottomBar = {
            NavigationBar {
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
            when (page) {
                Page.DIGEST -> DigestPage(vm)
                Page.NEWS -> FeedPage(vm)
                Page.SOURCES -> SourcesPage(vm)
                Page.SETTINGS -> SettingsPage(vm)
            }
        }
    }
}

@Composable
private fun DigestPage(vm: NewsViewModel) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val digest by vm.digest.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val news by vm.articles.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Главное за 24 часа", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${news.size} публикаций · ${sources.count { it.enabled }} источника(ов)",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == DigestMode.SHORT, onClick = { vm.changeMode(DigestMode.SHORT) },
                label = { Text("За 2 минуты") })
            FilterChip(selected = mode == DigestMode.FULL, onClick = { vm.changeMode(DigestMode.FULL) },
                label = { Text("Подробнее") })
        }
        Button(onClick = { vm.generate() }, enabled = !generating && news.isNotEmpty() && hasKey,
            modifier = Modifier.fillMaxWidth()) {
            if (generating) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).height(20.dp), strokeWidth = 2.dp)
            Text(if (generating) "ИИ готовит сводку…" else "✦ Создать ИИ-сводку")
        }
        if (!hasKey) Text("Для ИИ-сводки добавь свой API-ключ в настройках.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (news.isEmpty()) Text("Новостей пока нет. Нажми «Обновить» и проверь подключённые источники.")

        if (digest != null) {
            val current = digest!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Сводка дня", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("По ${current.articleCount} публикациям · ${formatDate(current.createdAt)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(current.content, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            Text("Оригинальные публикации", style = MaterialTheme.typography.titleMedium)
            val refs = remember(current.referencesJson) {
                val json = JSONArray(current.referencesJson)
                (0 until minOf(40, json.length())).map { json.getJSONObject(it) }
            }
            refs.forEach { ref ->
                val url = ref.optString("url")
                Text(
                    "[${ref.optInt("number")}] ${ref.optString("source")} — ${ref.optString("title")}",
                    modifier = Modifier.fillMaxWidth().clickable { if (url.startsWith("https://")) uriHandler.openUri(url) }
                        .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пока нет сводки", style = MaterialTheme.typography.titleMedium)
                    Text("После обновления новостей нажми кнопку выше. ИИ сгруппирует события и поставит ссылки на источники.")
                }
            }
        }
    }
}

@Composable
private fun FeedPage(vm: NewsViewModel) {
    val articles by vm.articles.collectAsStateWithLifecycle()
    if (articles.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
            Text("Новостей за последние 24 часа нет. Обнови ленту на верхней панели.")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Последние 24 часа · ${articles.size}", style = MaterialTheme.typography.titleMedium) }
            items(articles, key = { it.id }) { article -> NewsCard(article) }
        }
    }
}

@Composable
private fun NewsCard(article: ArticleEntity) {
    val handler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().clickable { handler.openUri(article.link) }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${article.sourceName} · ${formatDate(article.publishedAt)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (article.excerpt.isNotBlank()) Text(article.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("Открыть оригинал ↗", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SourcesPage(vm: NewsViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Мои источники", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { dialogVisible = true }) { Text("+ Добавить") }
        }
        Text("RSS и публичные Telegram-каналы. Telegram-превью — экспериментальный способ сбора.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            items(sources, key = { it.id }) { source ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(source.kind, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = source.enabled, onCheckedChange = { vm.toggleSource(source.id, it) })
                        }
                        Text(source.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.deleteSource(source.id) }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (dialogVisible) AddSourceDialog(onClose = { dialogVisible = false }) { name, kind, url ->
        vm.addSource(name, kind, url)
        dialogVisible = false
    }
}

@Composable
private fun AddSourceDialog(onClose: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(SourceKind.RSS) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Подключить источник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == SourceKind.RSS, onClick = { kind = SourceKind.RSS },
                        label = { Text("RSS") })
                    FilterChip(selected = kind == SourceKind.TELEGRAM, onClick = { kind = SourceKind.TELEGRAM },
                        label = { Text("Telegram") })
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Название") })
                OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true,
                    label = { Text(if (kind == SourceKind.RSS) "HTTPS-ссылка на RSS" else "@канал или https://t.me/канал") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, kind, address) }, enabled = name.isNotBlank() && address.isNotBlank()) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}

@Composable
private fun SettingsPage(vm: NewsViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var modelDraft by remember(config.provider) { mutableStateOf(config.model) }
    var keyDraft by remember(config.provider) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale("ru"))
private fun formatDate(millis: Long): String = dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
