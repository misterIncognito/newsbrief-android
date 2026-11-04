package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.WebCollectors
import io.newsbrief.app.network.ArticleLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class TelegramParsingTest {
    private val parser = io.newsbrief.app.network.TelegramPreviewParser()
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
        val posts = parser.parse(html, source)
        assertEquals(1, posts.size)
        assertTrue(posts.first().link.endsWith("/321"))
    }

    @Test fun telegramPostsKeepFullTextParagraphsAndPhotos() {
        val source = SourceEntity(1, "BBC Telegram", "https://t.me/s/bbcrussian", SourceKind.TELEGRAM)
        val longText = "Подробный текст. ".repeat(150)
        val html = """<div class="tgme_widget_message_wrap">
            <div class="tgme_widget_message" data-post="bbcrussian/123">
            <div class="tgme_widget_message_text"><p>Начало.</p><p>$longText</p></div>
            <a class="tgme_widget_message_photo_wrap" style="background-image:url('https://cdn.example/photo.jpg')"></a>
            <time datetime="2026-10-08T12:00:00Z"></time></div></div>"""
        val item = parser.parse(html, source).single()
        assertTrue(item.content.length > 1600)
        assertTrue(item.content.startsWith("Начало.\n\n"))
        assertEquals("https://cdn.example/photo.jpg", item.imageUrl)
        assertEquals("https://t.me/bbcrussian/123", item.link)
    }

    @Test fun extractsLinksFromAttachedPreviewWithoutCollectingTelegramNavigation() {
        val source = SourceEntity(1, "News", "https://t.me/s/channel", SourceKind.TELEGRAM)
        val html = """<div class="tgme_widget_message" data-post="channel/42">
            <div class="tgme_widget_message_text">A post with a media card</div>
            <a class="tgme_widget_message_link_preview" href="https://publisher.example/story">
              <div class="link_preview_site_name">Publisher</div>
              <i class="link_preview_image" style="background-image:url('https://cdn.example/card.jpg')"></i>
              <div class="link_preview_title">Read the full story</div>
            </a>
            <a class="tgme_widget_message_date" href="https://t.me/channel/42">date</a>
            <time datetime="2026-10-08T12:00:00Z"></time>
        </div>"""
        val post = parser.parse(html, source).single()
        val links = ArticleLinks.decode(post.linksJson)
        assertEquals(listOf("https://publisher.example/story"), links.map { it.url })
        assertEquals("Read the full story", links.single().label)
        assertEquals("https://cdn.example/card.jpg", post.imageUrl)
        assertEquals("A post with a media card", post.content)
    }

    @Test fun deduplicatesPreviewLinksAndRejectsUnsafeTargets() {
        val source = SourceEntity(1, "News", "https://t.me/s/channel", SourceKind.TELEGRAM)
        val html = """<div class="tgme_widget_message" data-post="channel/43">
            <div class="tgme_widget_message_text">Read <a href="https://publisher.example/story">here</a></div>
            <a class="tgme_widget_message_link_preview" href="https://publisher.example/story">duplicate</a>
            <a class="tgme_widget_message_link_preview" href="javascript:alert(1)">unsafe</a>
        </div>"""
        val links = ArticleLinks.decode(parser.parse(html, source).single().linksJson)
        assertEquals(1, links.size)
        assertEquals("here", links.single().label)
    }

    @Test fun bbcPostKeepsBothInlineArticleAndAttachedPreview() {
        val source = SourceEntity(1, "BBC", "https://t.me/s/bbcrussian", SourceKind.TELEGRAM)
        val html = """<div class="tgme_widget_message" data-post="bbcrussian/101052">
            <div class="media_supported_cont"><div class="tgme_widget_message_text">
              BBC report with an <a href="https://www.bbc.com/russian/articles/cxly47zyn1p8o">inline link</a>.
            </div></div>
            <a class="tgme_widget_message_link_preview" href="https://bbc.com/russian/articles/cxly47zyn1p8o">
              <i class="link_preview_image" style="background-image:url('https://cdn.example/bbc-preview.jpg')"></i>
              <div class="link_preview_title">BBC article preview</div>
            </a>
        </div>"""
        val post = parser.parse(html, source).single()
        assertEquals("https://t.me/bbcrussian/101052", post.link)
        assertEquals("https://cdn.example/bbc-preview.jpg", post.imageUrl)
        assertEquals(listOf("inline link", "BBC article preview"), ArticleLinks.decode(post.linksJson).map { it.label })
    }

    @Test fun replyUsesActualMessageAndItsLinksRatherThanQuotedPreview() {
        val source = SourceEntity(1, "Channel", "https://t.me/s/channel", SourceKind.TELEGRAM)
        val html = """<div class="tgme_widget_message" data-post="channel/42">
            <a class="tgme_widget_message_reply" href="https://t.me/channel/41">
                <div class="tgme_widget_message_text js-message_reply_text">Quoted old preview…</div>
            </a>
            <div class="tgme_widget_message_text js-message_text">Actual post <a href="https://publisher.example/story">Full story</a></div>
        </div>"""
        val post = parser.parse(html, source).single()
        assertEquals("Actual post Full story", post.content)
        assertEquals("https://publisher.example/story", ArticleLinks.decode(post.linksJson).single().url)
    }

    @Test fun missingMessageBodyNeverUsesQuotedPreviewAsThePost() {
        val source = SourceEntity(1, "Channel", "https://t.me/s/channel", SourceKind.TELEGRAM)
        val html = """<div class="tgme_widget_message" data-post="channel/42">
            <a class="tgme_widget_message_reply" href="https://t.me/channel/41">
                <div class="tgme_widget_message_text js-message_reply_text">Quoted old preview…</div>
            </a></div>"""
        assertTrue(parser.parse(html, source).single().content.isEmpty())
    }

    @Test fun hydrationRepairsCachedQuotedPreviewButPreservesOtherOfflineText() = kotlinx.coroutines.runBlocking {
        val html = """<div class="tgme_widget_message" data-post="channel/42">
            <a class="tgme_widget_message_reply" href="https://t.me/channel/41"><div class="tgme_widget_message_text js-message_reply_text">Quoted old preview…</div></a>
            <div class="tgme_widget_message_text js-message_text">Actual post <a href="https://publisher.example/story">Full story</a></div>
        </div>"""
        val post = io.newsbrief.app.data.ArticleEntity(sourceId = 1, sourceName = "Channel", title = "Old", excerpt = "", content = "Quoted old preview…", link = "https://t.me/channel/42", publishedAt = 1)
        val cached = io.newsbrief.app.network.ReaderArticle("Old", post.content, "", post.link)
        val loader = io.newsbrief.app.network.TelegramArticleContent(io.newsbrief.app.TestSettings())
        val repaired = loader.load(post, cached, "https://t.me/s/channel/42") { _, _, _, _ -> html.toByteArray() }
        assertEquals("Actual post Full story", repaired.text)
        assertEquals("https://publisher.example/story", repaired.links.single().url)
        val kept = loader.load(post.copy(content = "My saved text"), cached.copy(text = "My saved text"), "https://t.me/s/channel/42") { _, _, _, _ -> html.toByteArray() }
        assertEquals("My saved text", kept.text)
    }

}
