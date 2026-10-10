package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.WebCollectors
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

}
