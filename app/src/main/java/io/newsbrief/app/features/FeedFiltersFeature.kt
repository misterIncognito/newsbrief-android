package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.FeedFilters
import io.newsbrief.app.ui.FeedFilterControls

class FeedFiltersFeature : AppFeature {
    override val id = "feed-filters"
    override fun configure(bindings: FeatureBindings) {
        bindings.filterOptions = FeedFilters::options
        bindings.feedControls += { FeedFilterControls(it) }
    }
}
