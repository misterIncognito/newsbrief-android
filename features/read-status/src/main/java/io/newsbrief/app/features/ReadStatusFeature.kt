package io.newsbrief.app.features

import io.newsbrief.app.runtime.AppFeature
import io.newsbrief.app.runtime.FeatureBindings
import io.newsbrief.app.ui.ReadStatusUi
import io.newsbrief.app.ui.ReadStatusViewModel
import io.newsbrief.app.ui.UnreadFilter
import io.newsbrief.app.ui.SavedUi
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class ReadStatusFeature : AppFeature {
    override val id = "read-status"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(ReadStatusUi::class.java, ::ReadStatusViewModel)
        bindings.feedControls += { host ->
            val saved = host.optional(SavedUi::class.java)?.savedOnly?.collectAsStateWithLifecycle()?.value ?: false
            if (!saved) UnreadFilter(host.controller(ReadStatusUi::class.java))
        }
    }
}
