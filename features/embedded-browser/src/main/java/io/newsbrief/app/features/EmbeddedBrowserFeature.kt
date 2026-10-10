package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.EmbeddedBrowserPage

class EmbeddedBrowserFeature : AppFeature {
    override val id = "embedded-browser"
    override fun configure(bindings: FeatureBindings) {
        bindings.browser = { url, worker, forceProxy -> EmbeddedBrowserPage(url, worker, forceProxy) }
    }
}
