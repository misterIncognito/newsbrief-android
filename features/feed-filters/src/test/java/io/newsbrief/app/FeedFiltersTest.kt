package io.newsbrief.app

import io.newsbrief.app.data.*
import org.junit.Assert.*
import org.junit.Test

class FeedFiltersTest {
    private fun source(address: String, id: Long, enabled: Boolean = true) = SourceEntity(id = id, name = address, address = address, kind = if (address.contains("t.me/")) SourceKind.TELEGRAM else SourceKind.RSS, enabled = enabled)

    @Test fun technologyIncludesRelevantSourcesAndExcludesGeneralAndDisabledSources() {
        val sources = listOf(source("https://www.theverge.com/rss/index.xml", 1),
            source("https://habr.com/ru/rss/articles/?fl=ru", 2), source("https://t.me/s/vcnews", 3),
            source("https://news.zerkalo.io/rss/all.rss", 4), source("https://t.me/s/devby", 5, enabled = false))
        assertEquals(listOf(1L, 2L, 3L), FeedFilters.sourceIds(sources, FeedCategory.TECH, null))
        assertNull(FeedFilters.sourceIds(sources, FeedCategory.ALL, null))
    }
    @Test fun publisherFilterCombinesZerkaloRssAndTelegram() {
        val sources = listOf(source("https://news.zerkalo.io/rss/all.rss", 1),
            source("https://t.me/s/zerkalo_io", 2), source("https://t.me/s/bbcrussian", 3))
        assertEquals(listOf(1L, 2L), FeedFilters.sourceIds(sources, FeedCategory.ALL, "zerkalo"))
        assertEquals(1, FeedFilters.options(sources, FeedCategory.ALL).count { it.key == "zerkalo" })
        assertTrue(FeedFilters.sourceIds(sources, FeedCategory.TECH, "zerkalo")!!.isEmpty())
    }
    @Test fun customSourcesRemainIndividuallySelectableAndHaveOtherCategory() {
        val source = SourceEntity(id = 42, name = "My news", address = "https://custom.example/rss", kind = SourceKind.RSS)
        assertEquals(setOf(FeedCategory.OTHER), FeedFilters.categories(source))
        assertEquals(listOf(42L), FeedFilters.sourceIds(listOf(source), FeedCategory.OTHER, "source:42"))
        assertTrue(FeedFilters.sourceIds(listOf(source), FeedCategory.ALL, "deleted-source")!!.isEmpty())
    }
    @Test fun businessInsiderCanBeFilteredTogetherAcrossEditions() {
        val sources = listOf(source("https://www.theverge.com/rss/index.xml", 1),
            source("https://businessinsider.com.pl/.feed", 2),
            source("https://news.google.com/rss/search?q=site%3Abusinessinsider.com&hl=en-US&gl=US&ceid=US%3Aen", 3))
        assertEquals(2, FeedFilters.sourceIds(sources, FeedCategory.BUSINESS, "business-insider")!!.size)
        assertEquals(1, FeedFilters.sourceIds(sources, FeedCategory.TECH, null)!!.size)
    }
    @Test fun multiplePublishersAreCombinedWithoutIncludingDisabledOrUnselectedSources() {
        val sources = listOf(source("https://news.zerkalo.io/rss/all.rss", 1),
            source("https://t.me/s/zerkalo_io", 2), source("https://t.me/s/bbcrussian", 3),
            source("https://meduza.io/rss/all", 4), source("https://t.me/s/bbcrussian", 5, enabled = false),
            SourceEntity(id = 6, name = "Zerkalo proxy", kind = SourceKind.TELEGRAM_PROXY, address = "telegram-proxy:zerkalo_io"))
        assertEquals(listOf(1L, 2L, 3L, 6L), FeedFilters.sourceIds(sources, FeedCategory.ALL, setOf("zerkalo", "bbc-ru")))
        assertEquals(listOf(3L), FeedFilters.sourceIds(sources, FeedCategory.ALL, setOf("bbc-ru")))
        assertNull(FeedFilters.sourceIds(sources, FeedCategory.ALL, emptySet()))
        assertTrue(FeedFilters.sourceIds(sources, FeedCategory.ALL, setOf("deleted")).orEmpty().isEmpty())
    }

}
