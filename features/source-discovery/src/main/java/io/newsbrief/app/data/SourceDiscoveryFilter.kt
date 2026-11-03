package io.newsbrief.app.data

import io.newsbrief.app.network.DirectoryProvider
import io.newsbrief.app.network.DirectoryResults

/** Keep clearly political outlets out of the default technology browse results. */
internal object SourceDiscoveryFilter {
    private val politicalTitle = Regex(
        "politic|government|election|parliament|политик|правительств|парламент|выбор|polityk|rząd",
        RegexOption.IGNORE_CASE
    )
    fun apply(provider: DirectoryProvider, query: String, results: DirectoryResults): DirectoryResults {
        val technology = query.trim().lowercase(java.util.Locale.ROOT) in
            setOf("technology", "technologies", "tech", "технологии")
        if (provider != DirectoryProvider.RSS || !technology) return results
        return results.copy(items = results.items.filterNot { politicalTitle.containsMatchIn(it.title) })
    }
}
