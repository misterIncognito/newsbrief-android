package io.newsbrief.app.data

/**
 * Curated public feeds. Users can turn off or delete every source.
 * Telegram links point to the public web preview, not an authenticated Telegram API.
 */
data class SourcePreset(
    val name: String,
    val address: String,
    val kind: String,
    val category: String,
    val enabledByDefault: Boolean = false
) {
    fun asEntity() = SourceEntity(name = name, address = address, kind = kind)
}

object SourceCatalog {
    val presets = listOf(
        // Original defaults (v0.1).
        SourcePreset("BBC Русская служба", "https://feeds.bbci.co.uk/russian/rss.xml", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Зеркало", "https://news.zerkalo.io/rss/all.rss", SourceKind.RSS,
            "Беларусь", enabledByDefault = true),
        SourcePreset("BILD на русском", "https://t.me/s/BILD_Russian", SourceKind.TELEGRAM,
            "На русском", enabledByDefault = true),

        // Added in v0.2. Do not add Meduza again on upgrades.
        SourcePreset("Медуза", "https://meduza.io/rss/all", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Deutsche Welle — русский", "https://rss.dw.com/rdf/rss-ru-all", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Polsat News — Польша", "https://www.polsatnews.pl/rss/polska.xml", SourceKind.RSS,
            "Польша · PL", enabledByDefault = true),

        // Requested for v0.3. vc.ru's own RSS has a history of failures, so use
        // its public Telegram channel rather than silently claiming RSS works.
        SourcePreset("Хабр — статьи", "https://habr.com/ru/rss/articles/?fl=ru", SourceKind.RSS,
            "Технологии · RU", enabledByDefault = true),
        SourcePreset("vc.ru — бизнес и технологии", "https://t.me/s/vcnews", SourceKind.TELEGRAM,
            "Бизнес · RU", enabledByDefault = true),
        SourcePreset("The Warsaw — Варшава", "https://t.me/s/thewwarsaw", SourceKind.TELEGRAM,
            "Польша · RU", enabledByDefault = true),

        // Opt-in: additional channels or feeds can overlap with defaults.
        SourcePreset("Хабр — искусственный интеллект", "https://habr.com/ru/rss/hubs/artificial_intelligence/articles/?fl=ru", SourceKind.RSS,
            "Технологии · RU"),
        SourcePreset("dev.by — ИТ Беларуси", "https://t.me/s/devby", SourceKind.TELEGRAM,
            "Беларусь · IT"),
        SourcePreset("Notes from Poland", "https://notesfrompoland.com/feed/", SourceKind.RSS,
            "Польша · EN"),
        SourcePreset("Warsaw Insider", "https://warsawinsider.pl/feed/", SourceKind.RSS,
            "Варшава · EN"),
        SourcePreset("TechCrunch", "https://techcrunch.com/feed/", SourceKind.RSS,
            "Технологии · EN"),
        SourcePreset("Ars Technica", "https://arstechnica.com/feed/", SourceKind.RSS,
            "Технологии · EN"),
        SourcePreset("DW Главное (Telegram)", "https://t.me/s/dwglavnoe", SourceKind.TELEGRAM,
            "На русском"),
        SourcePreset("Медуза LIVE (Telegram)", "https://t.me/s/meduzalive", SourceKind.TELEGRAM,
            "На русском"),
        SourcePreset("Наша Ніва", "https://t.me/s/nashaniva", SourceKind.TELEGRAM,
            "Беларусь · BY"),
        SourcePreset("Белсат", "https://t.me/s/belsat", SourceKind.TELEGRAM,
            "Беларусь · BY"),
        SourcePreset("Polsat News — все новости", "https://www.polsatnews.pl/rss/wszystkie.xml", SourceKind.RSS,
            "Польша · PL"),
        SourcePreset("BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", SourceKind.RSS,
            "Мир · EN"),
        SourcePreset("The Guardian — World", "https://www.theguardian.com/world/rss", SourceKind.RSS,
            "Мир · EN"),
        SourcePreset("Al Jazeera — English", "https://www.aljazeera.com/xml/rss/all.xml", SourceKind.RSS,
            "Мир · EN")
    )

    private val legacyDefaults = setOf(
        "https://feeds.bbci.co.uk/russian/rss.xml",
        "https://news.zerkalo.io/rss/all.rss",
        "https://t.me/s/BILD_Russian"
    )

    private val v3Addresses = setOf(
        "https://habr.com/ru/rss/articles/?fl=ru",
        "https://t.me/s/vcnews",
        "https://t.me/s/thewwarsaw"
    )
    // V0.2 additions only (needed when upgrading directly from v0.1).
    val newDefaults = presets.filter {
        it.enabledByDefault && it.address !in legacyDefaults && it.address !in v3Addresses
    }
    val newDefaultsV3 = presets.filter { it.enabledByDefault && it.address in v3Addresses }

    /** Prevent deleted presets from reappearing on app startup after migration. */
    fun defaultsToSeed(sourceCount: Int, v2Seeded: Boolean, v3Seeded: Boolean): List<SourcePreset> {
        if (v2Seeded && v3Seeded) return emptyList()
        if (sourceCount == 0 && !v2Seeded) return presets.filter { it.enabledByDefault }
        return (if (v2Seeded) emptyList() else newDefaults) +
            (if (v3Seeded) emptyList() else newDefaultsV3)
    }
}
