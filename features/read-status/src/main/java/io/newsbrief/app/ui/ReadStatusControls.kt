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

private val UnreadPostsIcon = ImageVector.Builder(name = "Unread", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 4f); horizontalLineTo(4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
        verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
        verticalLineTo(6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
        moveTo(20f, 8f); lineTo(12f, 13f); lineTo(4f, 8f); verticalLineTo(6f)
        lineTo(12f, 11f); lineTo(20f, 6f); close()
    }
}.build()

@Composable fun UnreadFilter(status: ReadStatusUi) {
    val unread by status.unreadOnly.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val message = stringResource(if (unread) R.string.read_show_all else R.string.read_show_unread)
    IconToggleButton(checked = unread, onCheckedChange = { selected ->
        status.setUnreadOnly(selected)
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    },
        modifier = Modifier.testTag("unread-filter")) {
        Icon(UnreadPostsIcon, contentDescription = stringResource(R.string.read_unread_only))
    }
}
