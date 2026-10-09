package io.newsbrief.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity

interface ArticleAction {
    @Composable fun Content(article: ArticleEntity, vm: ReaderUi)
}
@Composable fun ArticleActionRow(article: ArticleEntity, vm: ReaderUi) {
    val actions = (vm as? NewsViewModel)?.bindings?.articleActions.orEmpty()
    if (actions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { it.Content(article, vm) }
    }
}
