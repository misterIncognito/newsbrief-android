package io.newsbrief.app.network

import io.newsbrief.app.data.*
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup

/** Returning null declines extraction; throwing is terminal. Routing is fixed by the request. */
interface ArticleFetchPlugin {
    val id: String
    fun supports(request: ArticleFetchRequest): Boolean
    suspend fun fetch(request: ArticleFetchRequest): ReaderArticle?
}

class ArticleFetchRequest(val article: ArticleEntity, val cached: ReaderArticle,
    private val worker: String, private val direct: (String, String, String, Int) -> ByteArray,
    private val proxy: (String, String, String, Int) -> ByteArray) {
    val mustProxy = article.proxyRequired || NewsRouting.requiresProxy(article.link)
    fun download(url: String, type: String = "fetch"): String =
        (if (mustProxy) proxy else direct)(url, type, worker, 2_500_000).toString(Charsets.UTF_8)
    // Both successes and failures are shared between extractors: one original-page request.
    val original: Result<String> by lazy {
        try { Result.success(download(article.link)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
    val originalReader: ReaderArticle by lazy {
        original.getOrNull()?.let { raw ->
            try { ArticleReader.parseArticle(raw, article.link) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        } ?: ReaderArticle("", "", "", article.link)
    }
}

class ArticleFetchRegistry(val plugins: List<ArticleFetchPlugin>) : ArticleExtractor {
    override suspend fun fetch(article: ArticleEntity, cached: ReaderArticle, worker: String,
        direct: (String, String, String, Int) -> ByteArray,
        proxy: (String, String, String, Int) -> ByteArray): ReaderArticle =
        fetch(ArticleFetchRequest(article, cached, worker, direct, proxy))
    init { require(plugins.map { it.id }.distinct().size == plugins.size) { "Duplicate article plugin ID" } }
    suspend fun fetch(request: ArticleFetchRequest): ReaderArticle {
        for (plugin in plugins) if (plugin.supports(request)) {
            plugin.fetch(request)?.let { return it }
        }
        error("Нет подходящего плагина для статьи")
    }
    companion object {
        fun builtIns(telegram: TelegramProxyClient) = ArticleFetchRegistry(listOf(
            TelegramProxyArticlePlugin(telegram), TelegramPreviewArticlePlugin(),
            AmpArticlePlugin(), WorkerArticlePlugin(), HtmlArticlePlugin()
        ))
    }
}

class TelegramProxyArticlePlugin(private val client: TelegramProxyClient) : ArticleFetchPlugin {
    override val id = "telegram-proxy-post"
    override fun supports(request: ArticleFetchRequest) = request.article.sourcePluginId == SourceKind.TELEGRAM_PROXY
    override suspend fun fetch(request: ArticleFetchRequest) = client.post(request.article)
}

class TelegramPreviewArticlePlugin : ArticleFetchPlugin {
    override val id = "telegram-preview-post"
    override fun supports(request: ArticleFetchRequest) = ArticleLinks.telegramPreview(request.article.link) != null
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle {
        val article = request.article
        val preview = ArticleLinks.telegramPreview(article.link)!!
        val post = FeedParsers().parseTelegramPreview(request.download(preview), SourceEntity(
            id = article.sourceId, name = article.sourceName, address = preview, kind = SourceKind.TELEGRAM))
            .firstOrNull { it.link == article.link } ?: error("Публикация Telegram недоступна")
        return request.cached.copy(text = article.content.ifBlank { post.content },
            imageUrl = request.cached.imageUrl.ifBlank { post.imageUrl },
            images = request.cached.images.ifEmpty { ArticleReader.decodeImages(post.imagesJson) },
            links = ArticleLinks.decode(post.linksJson))
    }
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

class WorkerArticlePlugin : ArticleFetchPlugin {
    override val id = "worker-readability"
    override fun supports(request: ArticleFetchRequest) = request.mustProxy && !ArticleLinks.isTelegramPost(request.article)
    override suspend fun fetch(request: ArticleFetchRequest): ReaderArticle = articleMetadata(
        ArticleReader.parseArticle(request.download(request.article.link, "article"), request.article.link), request.originalReader)
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
