package io.newsbrief.app.features

import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*
import io.newsbrief.app.ui.EmbeddedBrowserPage

class EmbeddedBrowserFeature : AppFeature {
    override val id = "embedded-browser"
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(BrowserUi::class.java, ::BrowserViewModel)
        bindings.navigation += NavigationHandler("browser", 10) { host, article -> host.controller(BrowserUi::class.java).openBrowser(article.link, article.proxyRequired) }
        bindings.navigation += NavigationHandler("article", 10) { host, article -> host.controller(BrowserUi::class.java).openBrowser(article.link, article.proxyRequired) }
        bindings.browser = { url, worker, forceProxy -> EmbeddedBrowserPage(url, worker, forceProxy) }
    }
}
