package io.newsbrief.app.features

import io.newsbrief.app.runtime.AppFeature
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.ui.ReadStatusUi
import io.newsbrief.app.ui.ReadStatusViewModel
import io.newsbrief.app.ui.UnreadFilter
import io.newsbrief.app.ui.SavedUi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable

class ReadStatusFeature : AppFeature {
    override val id = "read-status"
    override val order = 1
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(ReadStatusUi::class.java, ::ReadStatusViewModel)
    }
    @Composable override fun Toolbar(host: io.newsbrief.app.runtime.FeatureHost) {
        val saved = host.optional(SavedUi::class.java)?.savedOnly?.collectAsStateWithLifecycle()?.value ?: false
        if (!saved) UnreadFilter(host.controller(ReadStatusUi::class.java))
    }
}
