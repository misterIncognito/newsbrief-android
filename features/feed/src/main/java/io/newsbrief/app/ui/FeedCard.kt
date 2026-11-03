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

/** Minimal feed; richer presentation and article actions are optional contributions. */
@Composable fun BasicNewsCard(article: ArticleEntity, host: io.newsbrief.app.runtime.FeatureHost) {
    val color = LocalArticleCardColor.current
    Card(Modifier.fillMaxWidth().clickable { host.openArticle(article) },
        colors = if (color != null) CardDefaults.cardColors(containerColor = color, contentColor = androidx.compose.ui.graphics.Color(0xFF191919)) else CardDefaults.cardColors()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(article.sourceName + " · " + formatDate(article.publishedAt), style = MaterialTheme.typography.labelSmall)
            Text(article.title, style = MaterialTheme.typography.titleMedium)
            Text(article.excerpt, maxLines = 4)
            ArticleActionRow(article, host)
        }
    }
}
