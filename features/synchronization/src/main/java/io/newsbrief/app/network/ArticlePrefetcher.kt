package io.newsbrief.app.network

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.WorkerSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ArticlePrefetcher(private val repository: PrefetchArticleStore, private val settings: WorkerSettings,
    private val images: PrefetchImageStore,
    private val originalImageUrl: (String, String) -> String = ArticleImages::originalUrl) {
    data class Target(val article: ArticleEntity, val originSource: Long)
    data class Result(val articles: Int, val images: Int, val failures: Int)

    suspend fun prefetch(limit: Int = 12): Result {
        require(limit in 1..24)
        val targets = linkedMapOf<String, Target>()
        for (post in repository.prefetchCandidates()) {
            val primary = if (ArticleLinks.isTelegramPost(post))
                ArticleLinks.fullArticle(ArticleLinks.decode(post.linksJson)) else null
            val article = if (primary != null) repository.linkedArticle(post, primary.url) else post
            val previous = targets[article.link]
            targets[article.link] = Target(article.copy(proxyRequired = article.proxyRequired ||
                previous?.article?.proxyRequired == true), previous?.originSource ?: post.sourceId)
        }
        val pending = targets.values.filter { target ->
            needsText(target.article) || leadImage(target.article).let { it.isNotBlank() && !images.contains(it) }
        }
        val selected = fairBatch(pending, limit)
        val semaphore = Semaphore(2)
        return coroutineScope {
            val results = selected.map { target -> async {
                semaphore.withPermit { download(target.article) }
            } }.awaitAll()
            Result(results.sumOf { it.articles }, results.sumOf { it.images }, results.sumOf { it.failures })
        }
    }

    private fun needsText(article: ArticleEntity): Boolean =
        !ArticleLinks.isTelegramPost(article) && (!article.readerLoaded || article.content.isBlank())

    private fun leadImage(article: ArticleEntity): String = article.imageUrl.ifBlank {
        ArticleImages.decode(article.imagesJson).firstOrNull().orEmpty()
    }.let { if (it.isBlank()) it else originalImageUrl(it, settings.workerUrl()) }

    private suspend fun download(article: ArticleEntity): Result {
        var savedText = 0
        var savedImages = 0
        var failures = 0
        try {
            val result = if (needsText(article)) repository.readArticle(article, refresh = true, preserveLongerText = true)
                else ReaderArticle(article.title, article.content, article.imageUrl, article.link,
                    images = ArticleImages.decode(article.imagesJson))
            if (result.notice == null && needsText(article)) savedText++
            else if (result.notice != null) failures++
            val image = result.imageUrl.ifBlank { result.images.firstOrNull().orEmpty() }
            if (image.isNotBlank()) {
                val resource = originalImageUrl(image, settings.workerUrl())
                if (!images.contains(resource)) {
                    images.loadForArticle(resource, settings.workerUrl(), article)
                    savedImages++
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failures++ }
        return Result(savedText, savedImages, failures)
    }

    companion object {
        /** Take turns between sources so one high-volume channel cannot fill every batch. */
        fun fairBatch(targets: List<Target>, limit: Int): List<Target> {
            val groups = targets.groupBy { it.originSource }.values.map { it.iterator() }
            return buildList {
                while (size < limit && groups.any { it.hasNext() }) {
                    for (group in groups) if (group.hasNext() && size < limit) add(group.next())
                }
            }
        }
    }
}
