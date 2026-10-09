package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.WebCollectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
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


    @Test fun parsesAtomFullContentAndImage() {
        val source = SourceEntity(4, "Atom", "https://example.com/atom", SourceKind.RSS)
        val feed = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Подробная новость</title>
                <link rel="alternate" href="https://example.com/news/1" />
                <summary>Анонс</summary>
                <content type="html"><![CDATA[<p>Полный текст статьи с подробностями.</p>
                    <img src="https://example.com/news/pic.jpg"/>]]></content>
              </entry>
            </feed>
        """.trimIndent()
        val items = parser.parseFeed(feed, source)
        assertEquals(1, items.size)
        assertEquals("Полный текст статьи с подробностями.", items.first().content)
        assertEquals("https://example.com/news/pic.jpg", items.first().imageUrl)
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

    @Test fun parsesDeutscheWelleRdfRss() {
        val source = SourceEntity(3, "DW", "https://rss.dw.com/rdf/rss-ru-all", SourceKind.RSS)
        val feed = """<?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
              <item><title>Новость</title><link>https://www.dw.com/ru/test</link>
                <description>Подробности</description><dc:date>2026-10-08T12:10:00Z</dc:date>
              </item>
            </rdf:RDF>
        """.trimIndent()
        val items = parser.parseFeed(feed, source)
        assertEquals(1, items.size)
        assertEquals("Новость", items[0].title)
        assertEquals("Подробности", items[0].excerpt)
    }
    @Test fun preservesRssEncodedContentAndRelativeImages() {
        val feed = """<rss xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><item>
            <title>Story</title><link>https://example.com/story</link><description>Short</description>
            <content:encoded><![CDATA[<p>First.</p><p>Second.</p><img src="/photo.jpg"/>]]></content:encoded>
            </item></channel></rss>"""
        val item = parser.parseFeed(feed, SourceEntity(1, "RSS", "https://example.com/rss", SourceKind.RSS)).single()
        assertEquals("First.\n\nSecond.", item.content)
        assertEquals("https://example.com/photo.jpg", item.imageUrl)
    }
    @Test fun preservesAtomXhtmlContent() {
        val feed = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>XHTML</title>
            <link href="https://example.com/story"/><content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">
            <p>First.</p><p>Second.</p><img src="/photo.jpg"/></div></content></entry></feed>"""
        val item = parser.parseFeed(feed, SourceEntity(1, "Atom", "https://example.com/rss", SourceKind.RSS)).single()
        assertEquals("First.\n\nSecond.", item.content)
        assertEquals("https://example.com/photo.jpg", item.imageUrl)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsExternalEntityDocuments() {
        parser.parseFeed("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss/>",
            SourceEntity(1, "RSS", "https://example.com/rss", SourceKind.RSS))
    }
    @Test fun telegramPostsKeepFullTextParagraphsAndPhotos() {
        val source = SourceEntity(1, "BBC Telegram", "https://t.me/s/bbcrussian", SourceKind.TELEGRAM)
        val longText = "Подробный текст. ".repeat(150)
        val html = """<div class="tgme_widget_message_wrap">
            <div class="tgme_widget_message" data-post="bbcrussian/123">
            <div class="tgme_widget_message_text"><p>Начало.</p><p>$longText</p></div>
            <a class="tgme_widget_message_photo_wrap" style="background-image:url('https://cdn.example/photo.jpg')"></a>
            <time datetime="2026-10-08T12:00:00Z"></time></div></div>"""
        val item = parser.parseTelegramPreview(html, source).single()
        assertTrue(item.content.length > 1600)
        assertTrue(item.content.startsWith("Начало.\n\n"))
        assertEquals("https://cdn.example/photo.jpg", item.imageUrl)
        assertEquals("https://t.me/bbcrussian/123", item.link)
    }
}
