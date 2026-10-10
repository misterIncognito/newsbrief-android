package io.newsbrief.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.newsbrief.app.network.ArticleLinks
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity

@Composable
fun NewsCard(article: ArticleEntity, vm: ReaderUi) {
    val workerUrl by vm.workerUrl.collectAsStateWithLifecycle()
    if (ArticleLinks.isTelegramPost(article)) {
        LaunchedEffect(article.link) {
            if (ArticleLinks.decode(article.linksJson).isEmpty()) vm.resolvePostLinks(article)
        }
        TelegramFeedPost(article, workerUrl,
            onLink = { vm.openFeedLink(article, it) },
            onBrowser = { vm.openBrowser(it, forceProxy = true) },
            actions = { ArticleActionRow(article, vm) })
        return
    }
    Card(Modifier.fillMaxWidth().clickable { vm.openReader(article) }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (article.imageUrl.isNotBlank()) NewsImage(article.imageUrl, article.link, workerUrl, 145, article.proxyRequired)
            Text("${article.sourceName} · ${formatDate(article.publishedAt)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (article.excerpt.isNotBlank()) Text(article.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("Читать в NewsBrief →", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            ArticleActionRow(article, vm)
        }
    }
}
