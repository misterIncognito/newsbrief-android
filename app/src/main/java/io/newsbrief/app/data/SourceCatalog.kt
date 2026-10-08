package io.newsbrief.app.data

/**
 * Curated public feeds. All entries remain editable/removable by the user.
 * Telegram sources use the public preview page, not the Telegram API.
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
        // Original three sources: retain them for a fresh installation.
        SourcePreset("BBC Русская служба", "https://feeds.bbci.co.uk/russian/rss.xml", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Зеркало", "https://news.zerkalo.io/rss/all.rss", SourceKind.RSS,
            "Беларусь", enabledByDefault = true),
        SourcePreset("BILD на русском", "https://t.me/s/BILD_Russian", SourceKind.TELEGRAM,
            "На русском", enabledByDefault = true),

        // Added in the second catalog revision.
        SourcePreset("Медуза", "https://meduza.io/rss/all", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Deutsche Welle — русский", "https://rss.dw.com/rdf/rss-ru-all", SourceKind.RSS,
            "На русском", enabledByDefault = true),
        SourcePreset("Polsat News — Польша", "https://www.polsatnews.pl/rss/polska.xml", SourceKind.RSS,
            "Польша · PL", enabledByDefault = true),

        // Opt-in feeds avoid overwhelming users or duplicating DW/Meduza RSS posts.
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

    // Upgrade installs with existing 3 feeds only once; never restore deleted presets on startup.
    private val legacyDefaults = setOf(
        "https://feeds.bbci.co.uk/russian/rss.xml",
        "https://news.zerkalo.io/rss/all.rss",
        "https://t.me/s/BILD_Russian"
    )
    val newDefaults = presets.filter { it.enabledByDefault && it.address !in legacyDefaults }
}