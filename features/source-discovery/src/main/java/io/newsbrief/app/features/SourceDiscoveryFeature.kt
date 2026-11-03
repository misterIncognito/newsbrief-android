package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.source_discovery.R

import androidx.compose.runtime.Composable
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class SourceDiscoveryFeature : AppFeature {
    override val id = "source-discovery"
    override fun localizedTitle(context: Context): String = context.getString(R.string.nb_feature_source_discovery)
    override val title = "Каталог"
    override val order = 21
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(DirectoryUi::class.java, ::DirectoryViewModel)
        bindings.directory = { db, network ->
            val directory = SourceDirectoryRepository(db.catalogSearchDao(), network ?: PublicDirectoryNetwork())
            val search: suspend (DirectoryProvider, String, Int) -> DirectoryResults = { provider, query, offset -> directory.search(provider, query, offset) }
            search
        }
    }
    @Composable override fun Screen(host: FeatureHost) {
        val vm = host.controller(DirectoryUi::class.java)
        androidx.compose.runtime.LaunchedEffect(Unit) { if (vm.directoryState.value.results == null) vm.searchDirectory() }
        SourceDirectoryPage(vm)
    }
}
