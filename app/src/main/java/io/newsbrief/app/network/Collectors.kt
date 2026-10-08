package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.util.FeedRules
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface NewsCollector {
    suspend fun collect(source: SourceEntity): List<ArticleEntity>
}

/** Public feeds only; no credential or Telegram user session is required. */
class WebCollectors(private val settings: SettingsStore? = null) : NewsCollector {
    override suspend fun collect(source: SourceEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val requestUrl = NewsRouting.endpoint(
            source.address, "fetch", settings?.workerUrl() ?: NewsRouting.DEFAULT_WORKER
        )
        val document = fetch(requestUrl, proxied = NewsRouting.requiresProxy(source.address))
        when (source.kind) {
            SourceKind.RSS -> parseFeed(document, source)
            SourceKind.TELEGRAM -> parseTelegramPreview(document, source)
            else -> error("Неизвестный тип: ${source.kind}")
        }
    }

    private fun fetch(address: String, proxied: Boolean): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = !proxied
            setRequestProperty("User-Agent", "NewsBrief/0.1 (personal RSS reader)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, text/html, */*")
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            // Limits untrusted server response size; prevents very large feeds.
            return connection.inputStream.buffered().use { stream ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var size = 0
                while (true) {
                    val count = stream.read(chunk)
                    if (count == -1) break
                    size += count
                    require(size <= 3_000_000) { "Слишком большой ответ" }
                    out.write(chunk, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseFeed(xml: String, source: SourceEntity): List<ArticleEntity> {
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
        var capture = ""
        val buffer = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DTD не допускается в RSS")
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (name == "item" || name == "entry") {
                        inside = true
                        title = ""; link = ""; description = ""; fullText = ""; imageUrl = ""; date = ""
                    } else if (inside) {
                        if (name == "link") {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank() && (parser.getAttributeValue(null, "rel") in listOf(null, "alternate"))) link = href
                        }
                        if (name in setOf("thumbnail", "enclosure") ||
                            (name == "content" && (parser.prefix == "media" || parser.name.startsWith("media:")))) {
                            val image = parser.getAttributeValue(null, "url")
                            if (!image.isNullOrBlank() && image.startsWith("https://") && imageUrl.isBlank()) imageUrl = image
                        }
                        if (name in setOf("title", "link", "description", "summary", "encoded", "content", "pubdate", "published", "updated", "date")) {
                            capture = name
                            buffer.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inside && capture.isNotBlank()) buffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (inside && name == capture) {
                        val value = buffer.toString().trim()
                        when (capture) {
                            "title" -> title = Jsoup.parse(value).text()
                            "link" -> if (link.isBlank()) link = value
                            "description", "summary", "encoded", "content" -> {
                                val html = Jsoup.parse(value)
                                val image = html.selectFirst("img[src]")?.absUrl("src").orEmpty()
                                    .ifBlank { html.selectFirst("img[src]")?.attr("src").orEmpty() }
                                if (imageUrl.isBlank() && image.startsWith("https://")) imageUrl = image
                                val plain = html.text()
                                if (capture == "encoded" || capture == "content") {
                                    if (plain.length > fullText.length) fullText = plain
                                }
                                if (description.isBlank() || capture == "encoded") description = plain
                            }
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

    internal fun parseTelegramPreview(html: String, source: SourceEntity): List<ArticleEntity> {
        val page = Jsoup.parse(html)
        return page.select(".tgme_widget_message_wrap").mapNotNull { wrapper ->
            val message = wrapper.selectFirst(".tgme_widget_message") ?: return@mapNotNull null
            val postId = message.attr("data-post")
            if (!postId.matches(Regex("[A-Za-z0-9_]+/[0-9]+"))) return@mapNotNull null
            val content = wrapper.selectFirst(".tgme_widget_message_text")?.text()?.trim().orEmpty()
            if (content.isBlank()) return@mapNotNull null
            val published = wrapper.selectFirst("time[datetime]")?.attr("datetime").orEmpty()
            ArticleEntity(
                sourceId = source.id,
                sourceName = source.name,
                title = FeedRules.headline(content),
                excerpt = content.take(1_600),
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
