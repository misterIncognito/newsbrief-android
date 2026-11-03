package io.newsbrief.app

import io.newsbrief.app.data.MemoryNewsStore
import io.newsbrief.app.data.SourceDirectoryRepository
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.DirectoryNetwork
import io.newsbrief.app.network.DirectoryProvider
import io.newsbrief.app.network.DirectoryResults
import io.newsbrief.app.network.DirectorySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SourceDirectoryRepositoryTest {
    @Test fun filtersDefaultTechResultsOnNetworkAndCacheButNotPoliticalSearch() = runBlocking {
        val tech = DirectorySource("The Verge", "https://example.org/tech", SourceKind.RSS)
        val politics = DirectorySource("Technology – POLITICO", "https://example.org/politics", SourceKind.RSS)
        var calls = 0
        val network = object : DirectoryNetwork {
            override suspend fun search(provider: DirectoryProvider, query: String): DirectoryResults {
                calls++
                return DirectoryResults(listOf(tech, politics))
            }
        }
        val repository = SourceDirectoryRepository(MemoryNewsStore().catalogSearchDao(), network, now = { 1000L })
        assertEquals(listOf(tech), repository.search(DirectoryProvider.RSS, "technology").items)
        val cached = repository.search(DirectoryProvider.RSS, "technology")
        assertTrue(cached.cached)
        assertEquals(listOf(tech), cached.items)
        assertEquals(1, calls)
        assertEquals(listOf(tech, politics), repository.search(DirectoryProvider.RSS, "politics").items)
        assertEquals(2, calls)
    }
}
