package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.data.SourceSettings
import io.newsbrief.app.util.FeedRules
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale


class TelegramPreviewParser {
    fun parse(html: String, source: SourceEntity): List<ArticleEntity> {
        val page = Jsoup.parse(html, source.address)
        return page.select(".tgme_widget_message").mapNotNull { message ->
            val wrapper = message
            val postId = message.attr("data-post")
            if (!postId.matches(Regex("[A-Za-z0-9_]+/[0-9]+"))) return@mapNotNull null
            val textElement = wrapper.selectFirst(".tgme_widget_message_text")
            // Link cards live outside the message text, so text anchors alone miss them.
            val textLinks = textElement?.let { ArticleLinks.extract(it) }.orEmpty()
            val previewLinks = wrapper.select("a.tgme_widget_message_link_preview[href]").mapNotNull { preview ->
                val url = preview.absUrl("href")
                if (!ArticleLinks.valid(url)) return@mapNotNull null
                val label = preview.selectFirst(".link_preview_title")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: preview.selectFirst(".link_preview_site_name")?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: java.net.URI(url).host
                ArticleLink(label, url)
            }
            val links = (textLinks + previewLinks).distinctBy { it.url }.take(50)
            val content = textElement?.let { FeedMarkup.htmlText(it) }.orEmpty()
            if (content.isBlank()) return@mapNotNull null
            val published = wrapper.selectFirst("time[datetime]")?.attr("datetime").orEmpty()
            val images = wrapper.select(".tgme_widget_message_photo_wrap, .tgme_widget_message_video_thumb, " +
                ".tgme_widget_message_link_preview .link_preview_image, " +
                ".tgme_widget_message_link_preview .link_preview_right_image")
                .mapNotNull { photo ->
                    Regex("url\\(['\"]?(https://[^'\")]+)").find(photo.attr("style"))?.groupValues?.get(1)
                }.distinct()
            ArticleEntity(
                sourceId = source.id,
                sourceName = source.name,
                title = FeedRules.headline(content),
                excerpt = content.take(1_600),
                content = content.take(60_000),
                imageUrl = images.firstOrNull().orEmpty(),
                imagesJson = org.json.JSONArray(images).toString(),
                linksJson = ArticleLinks.encode(links),
                link = "https://t.me/$postId",
                publishedAt = parseDate(published) ?: System.currentTimeMillis()
            )
        }.take(100)
    }

    private fun parseDate(input: String): Long? {
        if (input.isBlank()) return null
        return runCatching { ZonedDateTime.parse(input, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(input).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(input).toEpochMilli() }
            .getOrNull()
    }

}
