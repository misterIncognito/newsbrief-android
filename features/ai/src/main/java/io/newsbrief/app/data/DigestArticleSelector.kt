package io.newsbrief.app.data

import io.newsbrief.app.util.FeedRules

/** Avoid letting a high-volume feed monopolize the 40 articles sent to the AI. */
object DigestArticleSelector {
    fun pick(articles: List<ArticleEntity>, limit: Int = 40): List<ArticleEntity> {
        if (limit <= 0) return emptyList()
        val unique = articles.sortedByDescending { it.publishedAt }
            .distinctBy { FeedRules.normalizedTitle(it.title) }
        val groups = unique.groupBy { it.sourceId }.values
            .sortedByDescending { it.first().publishedAt }
            .map { it.iterator() }
        val picked = mutableListOf<ArticleEntity>()
        while (picked.size < limit && groups.any { it.hasNext() }) {
            for (source in groups) {
                if (picked.size == limit) break
                if (source.hasNext()) picked += source.next()
            }
        }
        return picked
    }
}
