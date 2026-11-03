package io.newsbrief.app.ui
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.runtime.FeatureHost
interface ArticleAction { @Composable fun Content(article: ArticleEntity, host: FeatureHost) }
@Composable fun ArticleActionRow(article: ArticleEntity, host: FeatureHost) {
    if (host.bindings.articleActions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        host.bindings.articleActions.forEach { it.Content(article, host) }
    }
}
