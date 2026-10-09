package io.newsbrief.app.features

import io.newsbrief.app.data.*
import io.newsbrief.app.network.SourcePluginRegistry
import io.newsbrief.app.runtime.*

/** Registry and minimal defaults only; no management, catalog or provider implementation. */
class SourceProvidersFeature : AppFeature {
    override val id = "source-providers"
    override fun configure(bindings: FeatureBindings) {
        bindings.sources = { settings -> SourcePluginRegistry(bindings.sourcePlugins.map { it(settings) }) }
        bindings.seed = { db, settings ->
            if (bindings.sourceDefaults.isNotEmpty() && db.sourceDao().count() == 0 && !settings.isSourcesCatalogV5Seeded()) {
                bindings.sourceDefaults.forEach { db.sourceDao().insert(it) }
                settings.markSourcesCatalogV2Seeded(); settings.markSourcesCatalogV3Seeded()
                settings.markSourcesCatalogV4Seeded(); settings.markSourcesCatalogV5Seeded()
            }
        }
    }
}
