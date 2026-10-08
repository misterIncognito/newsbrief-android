package io.newsbrief.app

import io.newsbrief.app.data.SourceCatalog
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.util.FeedRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCatalogTest {
    @Test fun entriesHaveUniqueAndValidUrls() {
        val entries = SourceCatalog.presets
        assertEquals(entries.size, entries.map { it.address }.distinct().size)
        entries.forEach { entry ->
            assertTrue(entry.name.isNotBlank())
            assertEquals(entry.address, FeedRules.canonicalAddress(entry.address, entry.kind))
        }
    }

    @Test fun catalogueSupportsRssAndTelegram() {
        assertTrue(SourceCatalog.presets.any { it.kind == SourceKind.RSS })
        assertTrue(SourceCatalog.presets.any { it.kind == SourceKind.TELEGRAM })
        assertEquals(3, SourceCatalog.newDefaults.size)
        assertTrue(SourceCatalog.newDefaults.all { it.enabledByDefault })
        assertTrue(SourceCatalog.presets.any { !it.enabledByDefault })
    }
}