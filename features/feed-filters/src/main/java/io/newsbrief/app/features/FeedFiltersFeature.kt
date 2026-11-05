package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*
import io.newsbrief.app.data.FeedFilters
import io.newsbrief.app.network.DirectoryProvider
import io.newsbrief.app.ui.FeedFilterControls

class FeedFiltersFeature : AppFeature {
    override val id = "feed-filters"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(FeedFilterUi::class.java, ::FeedFiltersViewModel)
        bindings.filterOptions = FeedFilters::options
        bindings.feedControls.add { host ->
            ChannelFilterBar(host.controller(FeedFilterUi::class.java), host.services.store.articleDao(), host.optional(SavedUi::class.java))
        }
    }
    @Composable override fun Toolbar(host: FeatureHost, navigation: FeaturePageNavigation) {
        FeedFilterControls(host.controller(FeedFilterUi::class.java), host.optional(SavedUi::class.java),
            openSources = if (navigation.hasPage("source-management")) { { navigation.openPage("source-management") } } else null,
            searchOnline = if (navigation.hasPage("source-discovery") && host.optional(DirectoryUi::class.java) != null) { query ->
                val directory = host.controller(DirectoryUi::class.java)
                directory.directoryQuery(query)
                if (directory.directoryState.value.provider == DirectoryProvider.CURATED) directory.directoryProvider(DirectoryProvider.RSS)
                else directory.searchDirectory()
                navigation.openPage("source-discovery")
            } else null)
    }
}
