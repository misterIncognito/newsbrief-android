package io.newsbrief.app.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.NewsViewModel
import io.newsbrief.app.ui.ReaderUi
import io.newsbrief.app.ui.FeedUi
import io.newsbrief.app.ui.ArticleAction

interface AppFeature {
    val id: String
    val title: String get() = ""
    val hasSettings: Boolean get() = false
    val order: Int get() = 0
    fun configure(bindings: FeatureBindings) {}
    @Composable fun Screen(vm: NewsViewModel) {}
    @Composable fun Settings(vm: NewsViewModel) {}
}
interface ArticlePresentation {
    @Composable fun Card(article: ArticleEntity, vm: NewsViewModel)
    @Composable fun Reader(vm: NewsViewModel)
}
class FeatureBindings {
    val articleActions = mutableListOf<ArticleAction>()
    val feedControls = mutableListOf<@Composable (FeedUi) -> Unit>()
    var feedEmptyMessage: (FeedUi) -> String? = { null }
    var saveArticle: (suspend (NewsStore, ArticleEntity) -> Boolean)? = null
    val sourcePlugins = mutableListOf<(SourceSettings) -> NewsSourcePlugin>()
    val sourceDefaults = mutableListOf<SourceEntity>()
    val telegramPlugins = mutableListOf<(SourceSettings) -> TelegramContent>()
    val pageProxyPlugins = mutableListOf<PageProxyPlugin>()
    var pageRouter: (List<PageProxyPlugin>) -> PageRouter = { DirectPageRouter }
    val articlePlugins = mutableListOf<(SourceSettings) -> ArticleFetchPlugin>()
    fun telegramContent(settings: SourceSettings): TelegramContent? = telegramPlugins.takeIf { it.isNotEmpty() }
        ?.let { CombinedTelegramContent(it.map { factory -> factory(settings) }) }

    var persistentStorage: ((Context) -> Pair<NewsStore, AppSettings>)? = null
    var sources: ((AppSettings) -> SourceRegistry)? = null
    var seed: suspend (NewsStore, AppSettings) -> Unit = { _, _ -> }
    var directory: ((NewsStore, DirectoryNetwork?) -> (suspend (DirectoryProvider, String, Int) -> DirectoryResults))? = null
    var filterOptions: (List<SourceEntity>, FeedCategory) -> List<FeedSourceOption> = { sources, category ->
        sources.filter { it.enabled && (category == FeedCategory.ALL || runCatching {
            val tags = org.json.JSONArray(it.categoriesJson)
            (0 until tags.length()).any { index -> tags.getString(index) == category.name }
        }.getOrDefault(false)) }.map { FeedSourceOption("source:${it.id}", it.name, listOf(it.id)) }
    }
    var article: ((AppSettings, ArticleExtractor?) -> ArticleLoader)? = null
    var extractor: ((AppSettings) -> ArticleExtractor)? = null
    var generate: (suspend (List<ArticleEntity>, String, AppSettings) -> DigestEntity)? = null
    var refresh: ((NewsStore, NewsCollector, AppSettings, () -> Long, () -> Unit) -> (suspend () -> RefreshResult))? = null
    var prefetch: ((NewsRepository, AppSettings, Context) -> (suspend () -> io.newsbrief.app.sync.PrefetchOutcome))? = null
    var start: (Context) -> Unit = {}
    var feedUpdated: (Context) -> Unit = {}
    var images: ((Context) -> PrefetchImageStore)? = null
    var presentation: ArticlePresentation? = null
    var browser: (@Composable (String, String, Boolean) -> Unit)? = null
    companion object {
        fun create(features: List<AppFeature> = GeneratedFeatures.create()): FeatureBindings = FeatureBindings().also { b ->
            features.sortedBy { it.order }.forEach { it.configure(b) }
        }
    }
}
class FeatureRuntime(context: Context, val features: List<AppFeature> = GeneratedFeatures.create()) {
    val bindings = FeatureBindings.create(features)
    private val persistent = bindings.persistentStorage?.invoke(context)
    val store: NewsStore = persistent?.first ?: MemoryNewsStore()
    val settings: AppSettings = persistent?.second ?: MemorySettings()
    private val sourceRegistry = bindings.sources?.invoke(settings) ?: EmptySourceRegistry()
    val repository = NewsRepository(store, sourceRegistry, settings, sourcePlugins = sourceRegistry,
        extensions = bindings, onFeedUpdated = { bindings.feedUpdated(context) })
    val backgroundTasks = object : io.newsbrief.app.sync.BackgroundTasks {
        override suspend fun refresh() = repository.refresh()
        override suspend fun prefetch() = prefetchArticles?.invoke() ?: io.newsbrief.app.sync.PrefetchOutcome(0, 0, 0)
    }
    private val prefetchArticles = if (bindings.article != null) bindings.prefetch?.invoke(repository, settings, context) else null
    init { bindings.start(context) }
}
