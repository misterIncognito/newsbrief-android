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
        assertEquals(3, SourceCatalog.newDefaultsV3.size)
        assertTrue((SourceCatalog.newDefaults + SourceCatalog.newDefaultsV3).all { it.enabledByDefault })
        assertTrue(SourceCatalog.presets.any { !it.enabledByDefault })
    }

    @Test fun upgradeAddsOnlyNewDefaultsWithoutRestoringDeletedSources() {
        val freshInstall = SourceCatalog.defaultsToSeed(0, v2Seeded = false, v3Seeded = false)
        assertEquals(11, freshInstall.size)
        assertEquals(1, freshInstall.count { it.name == "Медуза" })
        val fromV1 = SourceCatalog.defaultsToSeed(3, v2Seeded = false, v3Seeded = false)
        assertEquals(8, fromV1.size)
        val fromV2 = SourceCatalog.defaultsToSeed(6, v2Seeded = true, v3Seeded = false, v4Seeded = true)
        assertEquals(setOf("Хабр — статьи", "vc.ru — бизнес и технологии", "The Warsaw — Варшава"),
            fromV2.map { it.name }.toSet())
        assertTrue(SourceCatalog.defaultsToSeed(3, v2Seeded = true, v3Seeded = true, v4Seeded = true).isEmpty())
        assertTrue(SourceCatalog.defaultsToSeed(0, v2Seeded = true, v3Seeded = true, v4Seeded = true).isEmpty())
    }
    @Test fun upgradeFromV3AddsOfficialTelegramChannelsOnce() {
        val additions = SourceCatalog.defaultsToSeed(9, v2Seeded = true, v3Seeded = true, v4Seeded = false)
        assertEquals(setOf("https://t.me/s/bbcrussian", "https://t.me/s/zerkalo_io"), additions.map { it.address }.toSet())
        assertTrue(additions.all { it.kind == SourceKind.TELEGRAM && it.enabledByDefault })
        assertTrue(SourceCatalog.defaultsToSeed(0, true, true, true).isEmpty())
    }
}