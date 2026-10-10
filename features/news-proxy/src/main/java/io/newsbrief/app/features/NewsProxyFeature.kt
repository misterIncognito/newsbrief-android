package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.*
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.*

class NewsProxyFeature : AppFeature {
    override val hasSettings = true
    override val id = "news-proxy"
    override fun configure(bindings: FeatureBindings) {
        bindings.pageProxyPlugins += CloudflarePageProxy()
        bindings.articlePlugins += { WorkerArticlePlugin() }
    }
    @Composable override fun Settings(vm: AppUi) { WorkerSettingsSection(vm) }
}
