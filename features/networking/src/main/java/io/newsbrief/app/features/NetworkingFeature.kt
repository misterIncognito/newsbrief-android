package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.network.NewsHttp

class NetworkingFeature : AppFeature {
    override val id = "networking"
    override fun configure(bindings: FeatureBindings) {
        bindings.http = NewsHttp.routed { bindings.pageRouter(bindings.pageProxyPlugins) }
    }
}
