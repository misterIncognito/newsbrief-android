package io.newsbrief.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.DigestMode
import org.json.JSONArray

@Composable
fun DigestPage(vm: AiUi) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val digest by vm.digest.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val news by vm.articles.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()

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
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (url.startsWith("https://")) vm.openLink(url, ref.optString("source"), ref.optString("title"))
                    }
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
