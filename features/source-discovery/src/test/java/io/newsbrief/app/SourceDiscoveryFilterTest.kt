package io.newsbrief.app

import io.newsbrief.app.data.SourceDiscoveryFilter
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.DirectoryProvider
import io.newsbrief.app.network.DirectoryResults
import io.newsbrief.app.network.DirectorySource
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceDiscoveryFilterTest {
    @Test fun defaultTechnologySearchHidesClearlyPoliticalOutletsButPoliticalSearchKeepsThem() {
        val tech = DirectorySource("The Verge", "https://example.org/tech", SourceKind.RSS)
        val politics = DirectorySource("Technology – POLITICO", "https://example.org/politics", SourceKind.RSS)
        val government = DirectorySource("Technology in government", "https://example.org/government", SourceKind.RSS)
        val results = DirectoryResults(listOf(tech, politics, government))
        assertEquals(listOf(tech), SourceDiscoveryFilter.apply(DirectoryProvider.RSS, "technology", results).items)
        assertEquals(results, SourceDiscoveryFilter.apply(DirectoryProvider.RSS, "politics", results))
        assertEquals(results, SourceDiscoveryFilter.apply(DirectoryProvider.TELEGRAM, "technology", results))
    }
}
