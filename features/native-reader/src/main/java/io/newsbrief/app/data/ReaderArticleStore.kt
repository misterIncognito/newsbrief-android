package io.newsbrief.app.data
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.FeatureServices
class ReaderArticleStore(services: FeatureServices) : PrefetchArticleStore {
    private val db = services.store
    private val articleReader = services.articleLoader
    override suspend fun lookup(url: String): ArticleEntity? = db.articleDao().findByLink(url)

    override suspend fun prefetchCandidates(): List<ArticleEntity> =
        db.articleDao().recent(System.currentTimeMillis() - 48 * 60 * 60_000L, 600)

    override suspend fun linkedArticle(parent: ArticleEntity, url: String): ArticleEntity {
        val saved = lookup(url) ?: ArticleEntity(sourceId = 0, sourceName = parent.sourceName,
            title = parent.title, excerpt = "", link = url, publishedAt = parent.publishedAt)
        val linked = saved.copy(proxyRequired = saved.proxyRequired || parent.proxyRequired ||
            io.newsbrief.app.network.ArticleLinks.isTelegramPost(parent))
        if (linked.proxyRequired && !saved.proxyRequired && saved.id != 0L) db.articleDao().requireProxy(url)
        return linked
    }

    override suspend fun readArticle(article: ArticleEntity, refresh: Boolean, preserveLongerText: Boolean): ReaderArticle {
        val stored = db.articleDao().findByLink(article.link) ?: article
        val saved = stored.copy(proxyRequired = stored.proxyRequired || article.proxyRequired)
        val loaded = articleReader?.load(saved, refresh) ?: return ReaderArticle(saved.title,
            saved.content.ifBlank { saved.excerpt }, saved.imageUrl, saved.link, publishedAt = saved.publishedAt,
            author = saved.author, images = io.newsbrief.app.network.ArticleImages.decode(saved.imagesJson),
            links = io.newsbrief.app.network.ArticleLinks.decode(saved.linksJson))
        val result = if (preserveLongerText && saved.content.length > loaded.text.length)
            loaded.copy(text = saved.content) else loaded
        if (result.notice == null && result.text.isNotBlank()) {
            db.articleDao().insertAll(listOf(saved))
            if (saved.proxyRequired) db.articleDao().requireProxy(saved.link)
            db.articleDao().updateReader(article.link, result.title, result.text, result.imageUrl,
                result.publishedAt ?: saved.publishedAt, result.author, org.json.JSONArray(result.images).toString(), io.newsbrief.app.network.ArticleLinks.encode(result.links))
        }
        return result
    }

}
