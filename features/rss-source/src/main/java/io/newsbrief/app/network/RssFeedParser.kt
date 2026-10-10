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


class FeedParsers {
    fun parseFeed(xml: String, source: SourceEntity): List<ArticleEntity> {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "DTD не допускается в RSS" }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        // External entities are not enabled. Reject DTD explicitly.
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        val items = mutableListOf<ArticleEntity>()
        var inside = false
        var title = ""
        var link = ""
        var description = ""
        var fullText = ""
        var imageUrl = ""
        var date = ""
        var author = ""
        val images = mutableListOf<String>()
        var capture = ""
        val buffer = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DTD не допускается в RSS")
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (name == "item" || name == "entry") {
                        inside = true
                        title = ""; link = ""; description = ""; fullText = ""; imageUrl = ""; date = ""; author = ""
                        capture = ""; buffer.setLength(0); images.clear()
                    } else if (inside) {
                        if (name == "link") {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank() && (parser.getAttributeValue(null, "rel") in listOf(null, "alternate"))) link = href
                        }
                        if (name == "thumbnail" || (name == "enclosure" &&
                            parser.getAttributeValue(null, "type").orEmpty().startsWith("image/")) ||
                            (name == "content" && (parser.prefix == "media" || parser.name.startsWith("media:")))) {
                            val image = parser.getAttributeValue(null, "url")
                            if (!image.isNullOrBlank() && image.startsWith("https://")) {
                                images += image
                                if (imageUrl.isBlank()) imageUrl = image
                            }
                        }
                        if (capture.isNotBlank()) {
                            buffer.append("<").append(name)
                            for (i in 0 until parser.attributeCount) {
                                buffer.append(" ").append(parser.getAttributeName(i)).append("=\"")
                                    .append(parser.getAttributeValue(i).replace("\"", "&quot;")).append("\"")
                            }
                            buffer.append(">")
                        }
                        if (capture.isBlank() && name in setOf("title", "link", "description", "summary", "encoded", "content", "pubdate", "published", "updated", "date", "author", "creator")) {
                            capture = name
                            buffer.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inside && capture.isNotBlank()) buffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (inside && capture.isNotBlank() && name != capture) buffer.append("</").append(name).append(">")
                    if (inside && name == capture) {
                        val value = buffer.toString().trim()
                        when (capture) {
                            "title" -> title = Jsoup.parse(value).text()
                            "link" -> if (link.isBlank()) link = value
                            "description", "summary", "encoded", "content" -> {
                                val html = Jsoup.parse(value, link.ifBlank { source.address })
                                images += html.select("img[src]").map { it.absUrl("src") }.filter { it.startsWith("https://") }
                                val image = html.selectFirst("img[src]")?.absUrl("src").orEmpty()
                                    .ifBlank { html.selectFirst("img[src]")?.attr("src").orEmpty() }
                                if (imageUrl.isBlank() && image.startsWith("https://")) imageUrl = image
                                val plain = FeedMarkup.htmlText(html)
                                if (capture == "encoded" || capture == "content") {
                                    if (plain.length > fullText.length) fullText = plain
                                }
                                if (description.isBlank() || capture == "encoded") description = plain
                            }
                            "author", "creator" -> author = Jsoup.parse(value).text()
                            "pubdate", "published", "updated", "date" -> if (date.isBlank()) date = value
                        }
                        capture = ""
                        buffer.setLength(0)
                    }
                    if (inside && (name == "item" || name == "entry")) {
                        if (link.startsWith("https://") && title.isNotBlank()) {
                            items += ArticleEntity(
                                sourceId = source.id,
                                sourceName = source.name,
                                title = title.take(240),
                                excerpt = description.take(1_600),
                                content = fullText.take(60_000),
                                imageUrl = imageUrl,
                                author = author,
                                imagesJson = org.json.JSONArray(images.distinct()).toString(),
                                link = link,
                                publishedAt = parseDate(date) ?: System.currentTimeMillis()
                            )
                        }
                        inside = false
                    }
                }
            }
            parser.next()
            if (items.size >= 120) break
        }
        return items
    }

    private fun parseDate(input: String): Long? {
        if (input.isBlank()) return null
        return runCatching { ZonedDateTime.parse(input, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(input).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(input).toEpochMilli() }
            .getOrNull()
    }
}
