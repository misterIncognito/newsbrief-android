package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.WebCollectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorsTest {
    private val parser = WebCollectors()

    @Test fun parsesBasicRss() {
        val source = SourceEntity(1, "BBC", "https://example.com/feed", SourceKind.RSS)
        val feed = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><item>
            <title>Пример новости</title>
            <link>https://example.com/post/100</link>
            <description><![CDATA[<p>Краткое содержание</p>]]></description>
            <pubDate>Thu, 08 Oct 2026 12:00:00 GMT</pubDate>
            </item></channel></rss>
        """.trimIndent()
        val posts = parser.parseFeed(feed, source)
        assertEquals(1, posts.size)
        assertEquals("Пример новости", posts.first().title)
        assertEquals("Краткое содержание", posts.first().excerpt)
        assertEquals("https://example.com/post/100", posts.first().link)
    }

    @Test fun parsesTelegramPublicPreview() {
        val source = SourceEntity(2, "BILD", "https://t.me/s/BILD_Russian", SourceKind.TELEGRAM)
        val html = """
            <div class="tgme_widget_message_wrap">
              <div class="tgme_widget_message" data-post="BILD_Russian/321">
                <div class="tgme_widget_message_text">Новость дня. Контекст новости.</div>
                <time datetime="2026-10-08T12:10:00+00:00"></time>
              </div>
            </div>
        """.trimIndent()
        val posts = parser.parseTelegramPreview(html, source)
        assertEquals(1, posts.size)
        assertTrue(posts.first().link.endsWith("/321"))
    }
}
