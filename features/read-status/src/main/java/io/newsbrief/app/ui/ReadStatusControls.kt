package io.newsbrief.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.modules.features.read_status.R

@Composable fun UnreadFilter(status: ReadStatusUi) {
    val unread by status.unreadOnly.collectAsStateWithLifecycle()
    FilterChip(selected = unread, onClick = { status.setUnreadOnly(!unread) },
        modifier = Modifier.padding(horizontal = 16.dp), label = { Text(stringResource(R.string.read_unread_only)) })
}
