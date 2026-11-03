package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

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
        Text(stringResource(R.string.nb_text_056), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.nb_digest_counts, news.size, sources.count { it.enabled }),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == DigestMode.SHORT, onClick = { vm.changeMode(DigestMode.SHORT) },
                label = { Text(stringResource(R.string.nb_text_057)) })
            FilterChip(selected = mode == DigestMode.FULL, onClick = { vm.changeMode(DigestMode.FULL) },
                label = { Text(stringResource(R.string.nb_text_058)) })
        }
        Button(onClick = { vm.generate() }, enabled = !generating && news.isNotEmpty() && hasKey,
            modifier = Modifier.fillMaxWidth()) {
            if (generating) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).height(20.dp), strokeWidth = 2.dp)
            Text(if (generating) stringResource(R.string.nb_generating) else stringResource(R.string.nb_create_summary))
        }
        if (!hasKey) Text(stringResource(R.string.nb_text_061), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (news.isEmpty()) Text(stringResource(R.string.nb_text_062))

        if (digest != null) {
            val current = digest!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.nb_text_063), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.nb_digest_meta, current.articleCount, formatDate(current.createdAt)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(current.content, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            Text(stringResource(R.string.nb_text_064), style = MaterialTheme.typography.titleMedium)
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
                    Text(stringResource(R.string.nb_text_065), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.nb_text_066))
                }
            }
        }
    }
}
