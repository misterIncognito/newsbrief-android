package io.newsbrief.app.network

import io.newsbrief.modules.features.telegram_source.R
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules
import org.jsoup.Jsoup
import java.net.URI

class TelegramPreviewSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.TELEGRAM, "Telegram", "@channel or https://t.me/channel", addressHintResource = R.string.nb_telegram_address_hint)
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = TelegramPreviewParser().parse(fetch(source.address), source)

    override suspend fun collectSince(source: SourceEntity): SourceBatch {
        val checkpoint = source.fetchCursor.takeIf { it.isNotBlank() }?.toLongOrNull()
        require(source.fetchCursor.isBlank() || checkpoint != null) { "Invalid Telegram source checkpoint" }
        val channel = URI(source.address).path.trim('/').substringAfterLast('/').lowercase(java.util.Locale.ROOT)
        val parser = TelegramPreviewParser()
        val posts = linkedMapOf<String, ArticleEntity>()
        var before: Long? = null
        var newest: Long? = null
        repeat(MAX_PAGES) {
            val address = if (before == null) source.address else "${source.address}?before=$before"
            val html = fetch(address)
            val ids = Jsoup.parse(html).select(".tgme_widget_message[data-post]").mapNotNull { message ->
                val parts = message.attr("data-post").split('/')
                if (parts.size == 2 && parts[0].lowercase(java.util.Locale.ROOT) == channel) parts[1].toLongOrNull() else null
            }
            if (ids.isEmpty()) {
                if (checkpoint == null && before == null) return SourceBatch(emptyList())
                return SourceBatch(posts.values.toList(), historyGap = true)
            }
            val oldest = ids.min()
            if (before != null && oldest >= before!!) error("Telegram returned a non-advancing history page")
            if (newest == null) newest = ids.max()
            parser.parse(html, source).forEach { posts.putIfAbsent(it.link, it) }
            if (checkpoint == null || oldest <= checkpoint) {
                return SourceBatch(posts.values.toList(), maxOf(checkpoint ?: 0L, newest!!).toString())
            }
            before = oldest
        }
        return SourceBatch(posts.values.toList(), historyGap = true)
    }

    companion object { private const val MAX_PAGES = 100 }
}
