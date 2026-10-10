package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class SourceDiscoveryFeature : AppFeature {
    override val id = "source-discovery"
    override val title = "Каталог"
    override val order = 21
    override fun configure(bindings: FeatureBindings) {
        bindings.directory = { db, network ->
            val directory = SourceDirectoryRepository(db.catalogSearchDao(), network ?: PublicDirectoryNetwork())
            val search: suspend (DirectoryProvider, String, Int) -> DirectoryResults = { provider, query, offset -> directory.search(provider, query, offset) }
            search
        }
    }
    @Composable override fun Screen(vm: AppUi) {
        androidx.compose.runtime.LaunchedEffect(Unit) { if (vm.directoryState.value.results == null) vm.searchDirectory() }
        SourceDirectoryPage(vm)
    }
}
