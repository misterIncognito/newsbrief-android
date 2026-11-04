package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.source_management.R

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class SourceManagementFeature : AppFeature {
    override val id = "source-management"
    override fun localizedTitle(context: Context): String = context.getString(R.string.nb_feature_source_management)
    override val title = "Источники"
    override val order = 20
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(SourcesUi::class.java, ::SourcesViewModel)
    }
    @Composable override fun Screen(host: FeatureHost, navigation: FeaturePageNavigation) {
        SourcesPage(host.controller(SourcesUi::class.java),
            openCatalog = if (navigation.hasPage("source-discovery")) { { navigation.openPage("source-discovery") } } else null)
    }
    @Composable override fun SetupScreen(host: FeatureHost, navigation: FeaturePageNavigation) {
        SourcesPage(host.controller(SourcesUi::class.java), initiallyAdding = true)
    }
}
