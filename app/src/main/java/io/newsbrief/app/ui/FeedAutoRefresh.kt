package io.newsbrief.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal fun FeedAutoRefresh(visible: Boolean, onRefresh: () -> Unit) {
    val refresh by rememberUpdatedState(onRefresh)
    LifecycleResumeEffect(visible) {
        if (visible) refresh()
        onPauseOrDispose { }
    }
}
