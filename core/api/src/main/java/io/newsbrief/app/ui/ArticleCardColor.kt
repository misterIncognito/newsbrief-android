package io.newsbrief.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Optional feed presentation supplied by the read-status feature. */
val LocalArticleCardColor = staticCompositionLocalOf<Color?> { null }
