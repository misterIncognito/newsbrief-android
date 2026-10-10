package io.newsbrief.app.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.AppUi
import io.newsbrief.app.ui.ReaderUi
import io.newsbrief.app.ui.FeedUi
import io.newsbrief.app.ui.ArticleAction

interface AppFeature {
    val id: String
    val title: String get() = ""
    val hasSettings: Boolean get() = false
    val order: Int get() = 0
    fun configure(bindings: FeatureBindings) {}
    @Composable fun Screen(vm: AppUi) {}
    @Composable fun Settings(vm: AppUi) {}
}
interface ArticlePresentation {
    @Composable fun Card(article: ArticleEntity, vm: AppUi)
    @Composable fun Reader(vm: AppUi)
}
class FeatureBindings {
    var http: HttpTransport? = null
    var ampAlternative: (String, String) -> String? = { _, _ -> null }
    var parseArticle: ((String, String) -> ReaderArticle)? = null
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
    var refresh: ((NewsStore, NewsCollector, AppSettings, () -> Long, FeedLoader) -> (suspend () -> RefreshResult))? = null
    var prefetch: ((PrefetchArticleStore, AppSettings, Context) -> (suspend () -> io.newsbrief.app.sync.PrefetchOutcome))? = null
    var start: (Context) -> Unit = {}
    var feedUpdated: (Context) -> Unit = {}
    var images: ((Context) -> PrefetchImageStore)? = null
    var presentation: ArticlePresentation? = null
    var browser: (@Composable (String, String, Boolean) -> Unit)? = null
    companion object {
        fun create(features: List<AppFeature> = InstalledFeatures.create()): FeatureBindings = FeatureBindings().also { b ->
            features.sortedBy { it.order }.forEach { it.configure(b) }
        }
    }
}
