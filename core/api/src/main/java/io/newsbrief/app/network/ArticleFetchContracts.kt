package io.newsbrief.app.network

import io.newsbrief.app.data.*
import kotlinx.coroutines.CancellationException

/** Returning null declines extraction; throwing is terminal. Routing is fixed by the request. */
interface ArticleFetchPlugin {
    val id: String
    fun supports(request: ArticleFetchRequest): Boolean
    suspend fun fetch(request: ArticleFetchRequest): ReaderArticle?
}

class ArticleFetchRequest(val article: ArticleEntity, val cached: ReaderArticle,
    private val worker: String, private val direct: (String, String, String, Int) -> ByteArray,
    private val proxy: (String, String, String, Int) -> ByteArray,
    val parseArticle: (String, String) -> ReaderArticle) {
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
            try { parseArticle(raw, article.link) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        } ?: ReaderArticle("", "", "", article.link)
    }
}
