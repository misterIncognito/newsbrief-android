package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.network.PageProxyRegistry

class PageProxyFeature : AppFeature {
    override val id = "page-proxy"
    override fun configure(bindings: FeatureBindings) { bindings.pageRouter = ::PageProxyRegistry }
}
