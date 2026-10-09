package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class SourcesFeature : AppFeature {
    override val id = "sources-discovery"
    override val title = "Источники"
    override val order = 20
    override fun configure(bindings: FeatureBindings) {
        bindings.telegram = { TelegramArticleContent(it) }
        bindings.sources = { SourcePluginRegistry.builtIns(it) }
        bindings.seed = { db, settings ->
            val v2 = settings.isSourcesCatalogV2Seeded(); val v3 = settings.isSourcesCatalogV3Seeded()
            val v4 = settings.isSourcesCatalogV4Seeded(); val v5 = settings.isSourcesCatalogV5Seeded()
            if (!(v2 && v3 && v4 && v5)) {
                SourceCatalog.defaultsToSeed(db.sourceDao().count(), v2, v3, v4, v5).forEach { db.sourceDao().insert(it.asEntity()) }
                settings.markSourcesCatalogV2Seeded(); settings.markSourcesCatalogV3Seeded()
                settings.markSourcesCatalogV4Seeded(); settings.markSourcesCatalogV5Seeded()
            }
        }
        bindings.directory = { db, network ->
            val directory = SourceDirectoryRepository(db.catalogSearchDao(), network ?: PublicDirectoryNetwork())
            val search: suspend (DirectoryProvider, String, Int) -> DirectoryResults = { provider, query, offset -> directory.search(provider, query, offset) }
            search
        }
        bindings.filterOptions = FeedFilters::options
    }
    @Composable override fun Screen(vm: NewsViewModel) {
        val catalog by vm.catalogOpen.collectAsStateWithLifecycle()
        if (catalog) SourceDirectoryPage(vm) else SourcesPage(vm)
    }
    @Composable override fun Settings(vm: NewsViewModel) { TelegramSettingsSection(vm) }
}
