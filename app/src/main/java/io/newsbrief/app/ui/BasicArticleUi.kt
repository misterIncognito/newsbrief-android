package io.newsbrief.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ArticleLinks

/** Feed content can be read and saved before full article extraction is installed. */
@Composable fun BasicNewsCard(article: ArticleEntity, vm: NewsViewModel) {
    val saved by vm.savedUrls.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth().clickable { vm.openReader(article) }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(article.sourceName + " · " + formatDate(article.publishedAt), style = MaterialTheme.typography.labelSmall)
            Text(article.title, style = MaterialTheme.typography.titleMedium)
            Text(article.excerpt, maxLines = 4)
            ArticleActions(article, article.link in saved) { vm.toggleSaved(article) }
        }
    }
}
@Composable fun BasicReader(vm: NewsViewModel) {
    val source by vm.readerSource.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val saved by vm.savedUrls.collectAsStateWithLifecycle()
    val article = source ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(article.sourceName + " · " + formatDate(article.publishedAt))
        Text(reader?.title ?: article.title, style = MaterialTheme.typography.headlineSmall)
        ArticleActions(article, article.link in saved) { vm.toggleSaved(article) }
        SelectionContainer { Text(reader?.text ?: article.content.ifBlank { article.excerpt }) }
        (reader?.links ?: ArticleLinks.decode(article.linksJson)).forEach { link ->
            OutlinedButton(onClick = { vm.openReaderLink(link.url) }) { Text(link.label) }
        }
    }
}
