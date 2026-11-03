package io.newsbrief.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.runtime.FeatureHost
import io.newsbrief.modules.features.read_status.R

class ReadStatusAction : ArticleAction {
    @Composable override fun Content(article: ArticleEntity, host: FeatureHost) {
        val status = host.controller(ReadStatusUi::class.java)
        val updates = remember(host, article.link) { host.services.store.articleDao().observeByLink(article.link) }
        val observed by updates.collectAsStateWithLifecycle(initialValue = article)
        val current = observed ?: article
        val description = stringResource(if (current.readAt == null) R.string.read_mark_read else R.string.read_mark_unread)
        IconButton(onClick = { status.toggleRead(current) }) {
            Icon(if (current.readAt == null) Icons.Default.Email else Icons.Default.Done, contentDescription = description)
        }
    }
}

@Composable fun UnreadFilter(status: ReadStatusUi) {
    val unread by status.unreadOnly.collectAsStateWithLifecycle()
    FilterChip(selected = unread, onClick = { status.setUnreadOnly(!unread) },
        modifier = Modifier.padding(horizontal = 16.dp), label = { Text(stringResource(R.string.read_unread_only)) })
}
