package io.newsbrief.app

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.DigestArticleSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestArticleSelectorTest {
    @Test fun representsQuietSourcesEvenWhenOneIsVeryBusy() {
        val busy = (1..100).map { article(1, "Main $it", it.toLong() + 1000) }
        val quiet = listOf(article(2, "Belarus important event", 999))
        val picked = DigestArticleSelector.pick(busy + quiet, 20)
        assertEquals(20, picked.size)
        assertTrue(picked.any { it.sourceId == 2L })
    }

    @Test fun avoidsIdenticalTitlesAcrossSources() {
        val picked = DigestArticleSelector.pick(listOf(
            article(1, "Главное: за день!", 3),
            article(2, "Главное за день", 2),
            article(2, "Другая новость", 1)
        ))
        assertEquals(2, picked.size)
    }

    private fun article(source: Long, title: String, date: Long) = ArticleEntity(
        sourceId = source, sourceName = "Source $source", title = title, excerpt = "",
        link = "https://example.com/$source/$date", publishedAt = date
    )
}