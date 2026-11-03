package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class SourceManagementFeature : AppFeature {
    override val id = "source-management"
    override val title = "Источники"
    override val order = 20
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(SourcesUi::class.java, ::SourcesViewModel)
    }
    @Composable override fun Screen(host: FeatureHost) { SourcesPage(host.controller(SourcesUi::class.java)) }
}
