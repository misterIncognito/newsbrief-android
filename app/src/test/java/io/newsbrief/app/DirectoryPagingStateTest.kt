package io.newsbrief.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.NewsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class DirectoryPagingStateTest {
    private val source = DirectorySource("AI News", "https://t.me/s/ai_news", SourceKind.TELEGRAM)
    private suspend fun withModel(network: DirectoryNetwork, block: suspend (NewsViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        val store = SettingsStore(app).apply {
            markSourcesCatalogV2Seeded(); markSourcesCatalogV3Seeded()
            markSourcesCatalogV4Seeded(); markSourcesCatalogV5Seeded()
        }
        val repo = NewsRepository(db, object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
        }, store, network)
        val model = NewsViewModel(app, repo, store)
        try { block(model) } finally {
            model.viewModelScope.cancel(); db.close(); Dispatchers.resetMain()
        }
    }
    @Test fun loadMoreAppendsUniqueChannelsAndFailureKeepsThePreviousPage() = runBlocking {
        var fail = false
        val network = object : DirectoryNetwork {
            override suspend fun search(provider: DirectoryProvider, query: String) =
                DirectoryResults(listOf(source), total = 9377, category = "ai", nextOffset = 25)
            override suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults {
                if (fail) error("Catalog temporarily unavailable")
                return DirectoryResults(listOf(source, source.copy(address = "https://t.me/s/other_ai")),
                    total = 9377, category = "ai", nextOffset = 50)
            }
        }
        withModel(network) { vm ->
            vm.directoryQuery("ИИ"); vm.directoryProvider(DirectoryProvider.TELEGRAM)
            withTimeout(5000) { vm.directoryState.first { !it.busy && it.results != null } }
            vm.loadMoreDirectory()
            withTimeout(5000) { vm.directoryState.first { !it.busy && it.results?.nextOffset == 50 } }
            assertEquals(2, vm.directoryState.value.results!!.items.size)
            fail = true; vm.loadMoreDirectory()
            withTimeout(5000) { vm.directoryState.first { !it.busy && it.error != null } }
            assertEquals(2, vm.directoryState.value.results!!.items.size)
            assertEquals(50, vm.directoryState.value.results!!.nextOffset)
        }
    }
    @Test fun changingTopicDuringPageLoadCancelsItAndNeverMixesCategories() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val network = object : DirectoryNetwork {
            override suspend fun search(provider: DirectoryProvider, query: String) =
                DirectoryResults(listOf(source), total = 9377, category = TelegramTopics.category(query), nextOffset = 25)
            override suspend fun more(provider: DirectoryProvider, query: String, offset: Int): DirectoryResults {
                started.complete(Unit)
                try { release.await() } finally { canceled.complete(Unit) }
                return DirectoryResults(listOf(source.copy(title = "Stale AI page")), total = 9377, category = "ai", nextOffset = 50)
            }
        }
        withModel(network) { vm ->
            vm.directoryQuery("ИИ"); vm.directoryProvider(DirectoryProvider.TELEGRAM)
            withTimeout(5000) { vm.directoryState.first { !it.busy && it.results != null } }
            vm.loadMoreDirectory(); withTimeout(5000) { started.await() }
            vm.directoryQuery("technology"); vm.searchDirectory()
            withTimeout(5000) { canceled.await(); vm.directoryState.first { !it.busy && it.results?.category == "tech" } }
            assertEquals("tech", vm.directoryState.value.results!!.category)
            assertFalse(vm.directoryState.value.results!!.items.any { it.title == "Stale AI page" })
            release.complete(Unit)
        }
    }
}
