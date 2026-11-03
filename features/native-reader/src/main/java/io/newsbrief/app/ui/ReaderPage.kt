package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.newsbrief.app.util.failureMessage
import io.newsbrief.modules.core.api.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import io.newsbrief.app.network.ArticleLinks
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReaderPage(vm: ReaderUi, host: io.newsbrief.app.runtime.FeatureHost) {
    val source by vm.readerSource.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val busy by vm.readerBusy.collectAsStateWithLifecycle()
    val worker by vm.workerUrl.collectAsStateWithLifecycle()
    val item = source ?: return
    val links = reader?.links ?: ArticleLinks.decode(item.linksJson)
    val primary = ArticleLinks.fullArticle(links)
    Column(Modifier.fillMaxSize().verticalScroll(remember(item.link) { androidx.compose.foundation.ScrollState(0) }).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = {
            val telegram = ArticleLinks.isTelegramPost(item)
            vm.openBrowser(if (telegram) primary?.url ?: item.link else item.link,
                forceProxy = item.proxyRequired || (telegram && primary != null))
        }, modifier = Modifier.fillMaxWidth()) { Text(if (vm.browserAvailable) stringResource(R.string.nb_open_builtin) else stringResource(R.string.nb_open_external)) }
        Text(item.sourceName + " · " + formatDate(reader?.publishedAt ?: item.publishedAt),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(reader?.title?.ifBlank { item.title } ?: item.title,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ArticleActionRow(item.copy(title = reader?.title?.ifBlank { item.title } ?: item.title), host)
        val image = reader?.imageUrl?.ifBlank { item.imageUrl } ?: item.imageUrl
        val images = reader?.images.orEmpty().ifEmpty { listOf(image).filter { it.isNotBlank() } }
        images.forEach { NewsImage(it, item.link, worker, 220, item.proxyRequired, host) }
        reader?.author?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        if (busy) CircularProgressIndicator()
        if (primary != null && ArticleLinks.isTelegramPost(item)) {
            Button(onClick = { vm.openReaderLink(primary.url) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nb_full_article, java.net.URI(primary.url).host))
            }
        }
        val content = reader?.text?.ifBlank { item.content.ifBlank { item.excerpt } }
            ?: item.content.ifBlank { item.excerpt }
        SelectionContainer {
            Text(content.ifBlank { stringResource(R.string.nb_article_unavailable) },
                style = MaterialTheme.typography.bodyLarge)
        }
        links.forEach { link ->
            OutlinedButton(onClick = { vm.openReaderLink(link.url) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(link.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(java.net.URI(link.url).host, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        reader?.notice?.let { Text(reader?.noticeFailure?.let { error ->
            stringResource(R.string.nb_reader_load_failed, LocalContext.current.failureMessage(error).take(90))
        } ?: it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { vm.openReader(item, refresh = true) }, enabled = !busy) {
            Text(stringResource(R.string.nb_text_025))
        }
        Text(item.link, style = MaterialTheme.typography.bodySmall)
    }
}
