package io.newsbrief.app.features

import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*

class RssSourceFeature : AppFeature {
    override val id = "rss-source"
    override fun configure(bindings: FeatureBindings) {
        bindings.sourcePlugins += { settings -> RssSourcePlugin { url ->
            NewsHttp.download(url, "fetch", settings.workerUrl(), 3_000_000).toString(Charsets.UTF_8)
        } }
        bindings.sourceDefaults += listOf(
            SourceEntity(name = "BBC Русская служба", address = "https://feeds.bbci.co.uk/russian/rss.xml", kind = SourceKind.RSS),
            SourceEntity(name = "The Verge", address = "https://www.theverge.com/rss/index.xml", kind = SourceKind.RSS, categoriesJson = "[\"TECH\"]")
        )
    }
}
