package io.newsbrief.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Apply to the source/date header whose visibility indicates that a post has been seen. */
val LocalFeedHeaderModifier = staticCompositionLocalOf<Modifier> { Modifier }
