package io.newsbrief.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity

class SaveArticleAction : ArticleAction {
    @Composable override fun Content(article: ArticleEntity, host: io.newsbrief.app.runtime.FeatureHost) {
        val vm = host.controller(SavedUi::class.java)
        val saved by vm.savedUrls.collectAsStateWithLifecycle()
        SaveArticleButton(article.link in saved) { vm.toggleSaved(article) }
    }
}
@Composable internal fun SaveArticleButton(saved: Boolean, onSave: () -> Unit) {
    TextButton(onClick = onSave) { Text(if (saved) "✓ Сохранено" else "Сохранить") }
}
@Composable fun SavedFeedControls(vm: SavedUi) {
    val savedOnly by vm.savedOnly.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!savedOnly, { vm.showSaved(false) }, label = { Text("Все новости") })
        FilterChip(savedOnly, { vm.showSaved(true) }, label = { Text("Сохранённые") })
    }
}
