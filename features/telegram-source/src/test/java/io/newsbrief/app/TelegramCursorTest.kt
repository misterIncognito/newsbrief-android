package io.newsbrief.app

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.TelegramPreviewSourcePlugin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelegramCursorTest {
    private fun page(vararg ids: Int) = ids.joinToString("") { id ->
        """<div class="tgme_widget_message" data-post="channel/$id"><div class="tgme_widget_message_text">Post $id</div></div>"""
    }
    private val source = SourceEntity(id = 4, name = "Channel", address = "https://t.me/s/channel", kind = SourceKind.TELEGRAM)

    @Test fun catchesUpAcrossPagesUntilPreviousCheckpoint() = runBlocking {
        val requests = mutableListOf<String>()
        val plugin = TelegramPreviewSourcePlugin { url ->
            requests += url
            when (url.substringAfter("?before=", "latest")) {
                "latest" -> page(105, 104)
                "104" -> page(103, 102)
                "102" -> page(101, 100)
                else -> error("Unexpected request")
            }
        }
        val batch = plugin.collectSince(source.copy(fetchCursor = "100"))
        assertEquals(listOf("latest", "104", "102"), requests.map { it.substringAfter("?before=", "latest") })
        assertEquals((100..105).toSet(), batch.articles.map { it.link.substringAfterLast('/').toInt() }.toSet())
        assertEquals("105", batch.nextCursor)
    }

    @Test fun firstFetchStartsFromCurrentPage() = runBlocking {
        val batch = TelegramPreviewSourcePlugin { page(105, 104) }.collectSince(source)
        assertEquals("105", batch.nextCursor)
        assertEquals(2, batch.articles.size)
    }

    @Test fun missingHistoryDoesNotProduceAnAdvancedCheckpoint() = runBlocking {
        val plugin = TelegramPreviewSourcePlugin { url -> if ('?' !in url) page(105, 104) else "" }
        val batch = plugin.collectSince(source.copy(fetchCursor = "100"))
        assertTrue(batch.historyGap)
        assertNull(batch.nextCursor)
        assertEquals(2, batch.articles.size)
    }

    @Test fun preservesMediaOnlyPostsInTheFeed() = runBlocking {
        val html = """<div class="tgme_widget_message" data-post="channel/12">
          <a class="tgme_widget_message_photo_wrap" style="background-image:url('https://cdn.example/image.jpg')"></a></div>"""
        val batch = TelegramPreviewSourcePlugin { html }.collectSince(source)
        assertEquals("https://t.me/channel/12", batch.articles.single().link)
        assertEquals("https://cdn.example/image.jpg", batch.articles.single().imageUrl)
        assertEquals("12", batch.nextCursor)
    }
}
