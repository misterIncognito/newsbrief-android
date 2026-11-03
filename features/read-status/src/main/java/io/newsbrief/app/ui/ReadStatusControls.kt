package io.newsbrief.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.modules.features.read_status.R

private val UnreadPostsIcon = ImageVector.Builder(name = "Unread posts", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 6f); horizontalLineTo(14f); verticalLineTo(8f); horizontalLineTo(3f); close()
        moveTo(3f, 11f); horizontalLineTo(21f); verticalLineTo(13f); horizontalLineTo(3f); close()
        moveTo(3f, 16f); horizontalLineTo(21f); verticalLineTo(18f); horizontalLineTo(3f); close()
        moveTo(19f, 3f); curveTo(20.66f, 3f, 22f, 4.34f, 22f, 6f); curveTo(22f, 7.66f, 20.66f, 9f, 19f, 9f)
        curveTo(17.34f, 9f, 16f, 7.66f, 16f, 6f); curveTo(16f, 4.34f, 17.34f, 3f, 19f, 3f); close()
    }
}.build()

@Composable fun UnreadFilter(status: ReadStatusUi) {
    val unread by status.unreadOnly.collectAsStateWithLifecycle()
    IconToggleButton(checked = unread, onCheckedChange = status::setUnreadOnly,
        modifier = Modifier.testTag("unread-filter")) {
        Icon(UnreadPostsIcon, contentDescription = stringResource(R.string.read_unread_only))
    }
}
