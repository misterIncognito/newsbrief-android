package io.newsbrief.app

import io.newsbrief.app.data.SourceCatalog
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.util.FeedRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCatalogTest {
    @Test fun curatedDirectoryDefaultsToTechAndPoliticalSearchRemainsAvailable() = kotlinx.coroutines.runBlocking {
        val directory = io.newsbrief.app.network.PublicDirectoryNetwork()
        val defaults = directory.search(io.newsbrief.app.network.DirectoryProvider.CURATED, "")
        assertTrue(defaults.items.any { it.title == "The Verge" && io.newsbrief.app.data.FeedCategory.TECH in it.categories })
        assertTrue(defaults.items.none { it.title == "Зеркало" })
        val politics = directory.search(io.newsbrief.app.network.DirectoryProvider.CURATED, "politics")
        assertTrue(politics.items.any { it.title == "Зеркало" })
    }
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
        assertEquals(14, freshInstall.size)
        assertEquals(1, freshInstall.count { it.name == "Медуза" })
        val fromV1 = SourceCatalog.defaultsToSeed(3, v2Seeded = false, v3Seeded = false)
        assertEquals(11, fromV1.size)
        val fromV2 = SourceCatalog.defaultsToSeed(6, v2Seeded = true, v3Seeded = false, v4Seeded = true, v5Seeded = true)
        assertEquals(setOf("Хабр — статьи", "vc.ru — бизнес и технологии", "The Warsaw — Варшава"),
            fromV2.map { it.name }.toSet())
        assertTrue(SourceCatalog.defaultsToSeed(3, v2Seeded = true, v3Seeded = true, v4Seeded = true, v5Seeded = true).isEmpty())
        assertTrue(SourceCatalog.defaultsToSeed(0, v2Seeded = true, v3Seeded = true, v4Seeded = true, v5Seeded = true).isEmpty())
    }
    @Test fun upgradeFromV3AddsOfficialTelegramChannelsOnce() {
        val additions = SourceCatalog.defaultsToSeed(9, v2Seeded = true, v3Seeded = true, v4Seeded = false, v5Seeded = true)
        assertEquals(setOf("https://t.me/s/bbcrussian", "https://t.me/s/zerkalo_io"), additions.map { it.address }.toSet())
        assertTrue(additions.all { it.kind == SourceKind.TELEGRAM && it.enabledByDefault })
        assertTrue(SourceCatalog.defaultsToSeed(0, true, true, true, true).isEmpty())
    }
    @Test fun upgradeAddsVergeAndBusinessInsiderWithoutReseedingOtherSources() {
        val added = SourceCatalog.defaultsToSeed(11, true, true, true, false)
        assertEquals(setOf("The Verge", "Business Insider Polska", "Business Insider (через Google News)"), added.map { it.name }.toSet())
        assertTrue(added.all { it.enabledByDefault })
        assertTrue(SourceCatalog.defaultsToSeed(0, true, true, true, true).isEmpty())
    }
    @Test fun catalogSearchFindsMissingSourcesIgnoringCaseAndWhitespace() {
        assertEquals("The Verge", SourceCatalog.search("  VERGE ").single().name)
        assertEquals(2, SourceCatalog.search("Business Insider").size)
        val defaults = SourceCatalog.search("")
        assertTrue(defaults.any { it.name == "The Verge" })
        assertTrue(defaults.any { it.name.contains("dev.by") })
        assertTrue(defaults.none { it.name == "BBC Русская служба" || it.name == "Зеркало" })
        assertEquals(defaults, SourceCatalog.search("technology"))
        assertTrue(SourceCatalog.search("artificial intelligence").any { it.name.contains("интеллект") })
        assertTrue(SourceCatalog.search("programming").any { it.name.contains("dev.by") })
        assertTrue(SourceCatalog.search("politics").any { it.name == "BBC Русская служба" })
        assertTrue(SourceCatalog.search("политика").any { it.name == "Зеркало" })
        assertTrue(SourceCatalog.search("bbc").any { it.name == "BBC Русская служба" })
        assertTrue(SourceCatalog.search("no such publisher").isEmpty())
    }
}
