package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.newsbrief.app.network.ArticleLinks
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun NewsCard(article: ArticleEntity, vm: ReaderUi, host: io.newsbrief.app.runtime.FeatureHost) {
    val workerUrl by vm.workerUrl.collectAsStateWithLifecycle()
    if (ArticleLinks.isTelegramPost(article)) {
        LaunchedEffect(article.link) {
            if (needsTelegramPreviewRefresh(article)) vm.resolvePostLinks(article)
        }
        TelegramFeedPost(article, workerUrl,
            onLink = { vm.openFeedLink(article, it) },
            onBrowser = { vm.openBrowser(it, forceProxy = true) },
            actions = { ArticleActionRow(article, host) }, host = host)
        return
    }
    val color = LocalArticleCardColor.current
    Card(Modifier.fillMaxWidth().clickable { vm.openReader(article) },
        colors = if (color != null) CardDefaults.cardColors(containerColor = color, contentColor = androidx.compose.ui.graphics.Color(0xFF191919)) else CardDefaults.cardColors()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (article.imageUrl.isNotBlank()) NewsImage(article.imageUrl, article.link, workerUrl, 145, article.proxyRequired, host)
            Text("${article.sourceName} · ${formatDate(article.publishedAt)}", modifier = LocalFeedHeaderModifier.current,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (article.excerpt.isNotBlank()) Text(article.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.nb_text_019), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            ArticleActionRow(article, host)
        }
    }
}

/** Old reply previews may already have acquired links while retaining truncated quoted text. */
internal fun needsTelegramPreviewRefresh(article: ArticleEntity): Boolean {
    if (ArticleLinks.telegramPreview(article.link) == null || article.sourcePluginId == io.newsbrief.app.data.SourceKind.TELEGRAM_PROXY) return false
    val content = article.content.trimEnd()
    return ArticleLinks.decode(article.linksJson).isEmpty() || content.endsWith("…") || content.endsWith("...")
}
