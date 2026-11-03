package io.newsbrief.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.modules.features.read_status.R

@Composable fun UnreadFilter(status: ReadStatusUi) {
    val unread by status.unreadOnly.collectAsStateWithLifecycle()
    IconToggleButton(checked = unread, onCheckedChange = status::setUnreadOnly,
        modifier = Modifier.testTag("unread-filter")) {
        Icon(Icons.Default.Email, contentDescription = stringResource(R.string.read_unread_only))
    }
}
