package io.newsbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.modules.features.feed.R
import kotlin.math.abs

@Composable
internal fun ReadStatusFrame(article: ArticleEntity, onToggle: (ArticleEntity) -> Unit, content: @Composable () -> Unit) {
    val currentArticle by rememberUpdatedState(article)
    val toggle by rememberUpdatedState(onToggle)
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val state = stringResource(if (article.readAt == null) R.string.read_status_unread else R.string.read_status_read)
    val action = stringResource(if (article.readAt == null) R.string.read_status_mark_read else R.string.read_status_mark_unread)
    Box(Modifier.fillMaxWidth().testTag("read-status-frame-${article.id}")
        .semantics {
            stateDescription = state
            customActions = listOf(CustomAccessibilityAction(action) { toggle(currentArticle); true })
        }
        .pointerInput(article.id, threshold) {
            var distance = 0f
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onDragCancel = { distance = 0f },
                onDragEnd = { if (abs(distance) >= threshold) toggle(currentArticle) },
                onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
            )
        }
        .background(if (article.readAt == null) Color(0xFF999999) else Color.White, RoundedCornerShape(14.dp))
        .padding(3.dp)) {
        content()
    }
}
