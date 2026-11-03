package io.newsbrief.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.DirectoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DirectorySourceSearchTest {
    @Test fun defaultRecommendationsAreTechAndSearchCanAddPoliticalSource() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        var added: DirectorySource? = null
        val bindings = FeatureBindings.create(emptyList())
        bindings.directory = { _, _ -> { provider, query, _ ->
            require(provider == DirectoryProvider.RSS)
            DirectoryResults(SourceCatalog.search(query).map { DirectorySource(it.name, it.address, it.kind, it.category) })
        } }
        bindings.sourceActions = { object : SourceActions {
            override suspend fun add(name: String, kind: String, address: String) = false
            override suspend fun connect(source: DirectorySource): Boolean { added = source; return true }
        } }
        val services = FeatureServices(MemoryNewsStore(), MemorySettings(), bindings = bindings)
        val navigator = object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }
        val host = FeatureHost(ApplicationProvider.getApplicationContext<Application>(), services, navigator) { }
        try {
            val vm = DirectoryViewModel(host)
            assertEquals(DirectoryProvider.RSS, vm.directoryState.value.provider)
            assertEquals("technology", vm.directoryState.value.query)
            vm.searchDirectory()
            dispatcher.scheduler.runCurrent()
            val defaults = vm.directoryState.value.results!!.items
            assertTrue(defaults.any { it.title == "The Verge" })
            assertTrue(defaults.none { it.title == "Зеркало" })
            vm.directoryQuery("politics")
            vm.searchDirectory()
            dispatcher.scheduler.runCurrent()
            val politicalSource = vm.directoryState.value.results!!.items.single { it.title == "Зеркало" }
            vm.addDirectorySource(politicalSource)
            dispatcher.scheduler.runCurrent()
            assertEquals(politicalSource, added)
            assertTrue(vm.directoryState.value.addMessage!!.contains("добавлен"))
        } finally {
            host.close()
            Dispatchers.resetMain()
        }
    }
}
