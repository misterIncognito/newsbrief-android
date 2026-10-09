package io.newsbrief.app.network

import io.newsbrief.app.data.*
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup

class ArticleFetchRegistry(val plugins: List<ArticleFetchPlugin>) : ArticleExtractor {
    override suspend fun fetch(article: ArticleEntity, cached: ReaderArticle, worker: String,
        direct: (String, String, String, Int) -> ByteArray,
        proxy: (String, String, String, Int) -> ByteArray): ReaderArticle =
        fetch(ArticleFetchRequest(article, cached, worker, direct, proxy, ArticleReader::parseArticle))
    init { require(plugins.map { it.id }.distinct().size == plugins.size) { "Duplicate article plugin ID" } }
    suspend fun fetch(request: ArticleFetchRequest): ReaderArticle {
        for (plugin in plugins) if (plugin.supports(request)) {
            plugin.fetch(request)?.let { return it }
        }
        error("Нет подходящего плагина для статьи")
    }
    companion object {
        fun builtIns(telegram: TelegramContent? = io.newsbrief.app.runtime.FeatureBindings.create().telegramContent(MemorySettings()),
            extra: List<ArticleFetchPlugin> = io.newsbrief.app.runtime.FeatureBindings.create().articlePlugins.map { it(MemorySettings()) }) = ArticleFetchRegistry(
            listOfNotNull(telegram?.let { TelegramContentArticlePlugin(it) }) +
                listOf(AmpArticlePlugin()) + extra + HtmlArticlePlugin()
        )
    }
}
class TelegramContentArticlePlugin(private val content: TelegramContent) : ArticleFetchPlugin {
    override val id = "telegram-content"
    override fun supports(request: ArticleFetchRequest) = content.supports(request.article)
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle = content.load(
        request.article, request.cached, ArticleLinks.telegramPreview(request.article.link)
    ) { url, type, _, _ -> request.download(url, type).toByteArray(Charsets.UTF_8) }
}

class AmpArticlePlugin : ArticleFetchPlugin {
    override val id = "amp"
    override fun supports(request: ArticleFetchRequest) = !ArticleLinks.isTelegramPost(request.article)
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle? {
        val raw = request.original.getOrNull() ?: return null
        if (raw.trimStart().startsWith("{")) return ArticleReader.parseArticle(raw, request.article.link)
        val amp = AmpDiscovery.alternative(raw, request.article.link) ?: return null
        return try {
            val ampRaw = request.download(amp)
            val document = Jsoup.parse(ampRaw, amp)
            require(document.selectFirst("article p,main p,[itemprop=articleBody] p") != null ||
                document.selectFirst("html[amp],html[⚡]") != null && document.selectFirst("body p") != null)
            val reader = ArticleReader.parseArticle(ampRaw, amp)
            require(reader.text.isNotBlank())
            articleMetadata(reader.copy(originalUrl = request.article.link), request.originalReader)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
}


class HtmlArticlePlugin : ArticleFetchPlugin {
    override val id = "publisher-html"
    override fun supports(request: ArticleFetchRequest) = !request.mustProxy && !ArticleLinks.isTelegramPost(request.article)
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle {
        request.original.getOrThrow()
        return request.originalReader
    }
}

internal fun articleMetadata(reader: ReaderArticle, fallback: ReaderArticle) = reader.copy(
    title = reader.title.ifBlank { fallback.title }, imageUrl = reader.imageUrl.ifBlank { fallback.imageUrl },
    author = reader.author.ifBlank { fallback.author }, publishedAt = reader.publishedAt ?: fallback.publishedAt,
    images = reader.images.ifEmpty { fallback.images })
