package io.newsbrief.app.data
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
class TestNewsRepository(val db: NewsStore, collector: NewsCollector, settings: AppSettings,
    directoryNetwork: DirectoryNetwork? = null, sourcePlugins: SourceRegistry? = null,
    articleReader: ArticleLoader? = null, clock: () -> Long = System::currentTimeMillis,
    onFeedUpdated: () -> Unit = {}, extensions: FeatureBindings = FeatureBindings.create()) : PrefetchArticleStore {
    val services = FeatureServices(db, settings, collector, sourcePlugins, directoryNetwork, articleReader, clock, onFeedUpdated, extensions)
    companion object { const val SYNC_COOLDOWN_MILLIS = 3 * 60_000L }
    fun observeSources() = db.sourceDao().observe()
    fun observeLatest() = db.articleDao().observeRecent(0)
    fun observeSaved() = db.articleDao().observeSaved()
    fun observeSavedUrls() = db.savedArticleDao().observeUrls()
    fun observeFeed(ids: List<Long>?) = db.articleDao().observeFiltered(ids.orEmpty(), ids != null)
    suspend fun toggleSaved(article: ArticleEntity) = checkNotNull(services.bindings.saveArticle)(db, article)
    suspend fun addSource(name: String, kind: String, address: String) = checkNotNull(services.sourceActions).add(name, kind, address)
    suspend fun addDiscoveredSource(source: DirectorySource) = checkNotNull(services.sourceActions).connect(source)
    suspend fun discover(provider: DirectoryProvider, query: String, offset: Int = 0) = checkNotNull(services.directory)(provider, query, offset)
    suspend fun toggleSource(id: Long, value: Boolean) = db.sourceDao().setEnabled(id, value)
    suspend fun removeSource(id: Long) = db.sourceDao().delete(id)
    suspend fun refresh() = services.refresh()
    override suspend fun lookup(url: String) = db.articleDao().findByLink(url)
    override suspend fun prefetchCandidates() = checkNotNull(services.articles).prefetchCandidates()
    override suspend fun linkedArticle(parent: ArticleEntity, url: String) = checkNotNull(services.articles).linkedArticle(parent, url)
    override suspend fun readArticle(article: ArticleEntity, refresh: Boolean, preserveLongerText: Boolean) =
        checkNotNull(services.articles).readArticle(article, refresh, preserveLongerText)
}
