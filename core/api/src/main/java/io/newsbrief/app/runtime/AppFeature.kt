package io.newsbrief.app.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import androidx.lifecycle.ViewModel
import io.newsbrief.app.ui.ArticleAction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface AppFeature {
    val id: String
    val title: String get() = ""
    val hasSettings: Boolean get() = false
    val order: Int get() = 0
    fun configure(bindings: FeatureBindings) {}
    @Composable fun Screen(host: FeatureHost) {}
    @Composable fun Settings(host: FeatureHost) {}
    @Composable fun Toolbar(host: FeatureHost) {}
}
interface ArticlePresentation {
    @Composable fun Card(article: ArticleEntity, host: FeatureHost)
}

/** A singleton capability must have exactly one provider in a composition. */
private fun <T : Any> singleProvider(): ReadWriteProperty<Any?, T?> = object : ReadWriteProperty<Any?, T?> {
    private var provider: T? = null
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = provider
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        require(value != null) { "Cannot clear provider: ${property.name}" }
        check(provider == null) { "Duplicate provider: ${property.name}" }
        provider = value
    }
}

class FeatureBindings {
    val controllers = mutableMapOf<Class<*>, (FeatureHost) -> ViewModel>()
    fun <T : Any> controller(type: Class<T>, factory: (FeatureHost) -> ViewModel) {
        check(controllers.putIfAbsent(type, factory) == null) { "Duplicate controller: ${type.name}" }
    }
    val commands = mutableMapOf<String, (FeatureHost) -> Unit>()
    val navigation = mutableListOf<NavigationHandler>()
    var feedLoader: ((FeatureServices) -> FeedLoader)? by singleProvider()
    var sourceActions: ((FeatureServices) -> SourceActions)? by singleProvider()
    var articleStore: ((FeatureServices) -> PrefetchArticleStore)? by singleProvider()
    var http: HttpTransport? by singleProvider()
    var ampAlternative: (String, String) -> String? = { _, _ -> null }
    var parseArticle: ((String, String) -> ReaderArticle)? by singleProvider()
    val articleActions = mutableListOf<ArticleAction>()
    val feedControls = mutableListOf<@Composable (FeatureHost) -> Unit>()
    var feedEmptyMessage: (FeatureHost) -> String? = { null }
    var saveArticle: (suspend (NewsStore, ArticleEntity) -> Boolean)? by singleProvider()
    val sourcePlugins = mutableListOf<(SourceSettings) -> NewsSourcePlugin>()
    val sourceDefaults = mutableListOf<SourceEntity>()
    val telegramPlugins = mutableListOf<(SourceSettings) -> TelegramContent>()
    val pageProxyPlugins = mutableListOf<PageProxyPlugin>()
    var pageRouter: (List<PageProxyPlugin>) -> PageRouter = { DirectPageRouter }
    val articlePlugins = mutableListOf<(SourceSettings) -> ArticleFetchPlugin>()
    fun telegramContent(settings: SourceSettings): TelegramContent? = telegramPlugins.takeIf { it.isNotEmpty() }
        ?.let { CombinedTelegramContent(it.map { factory -> factory(settings) }) }

    var persistentStorage: ((Context) -> Pair<NewsStore, AppSettings>)? by singleProvider()
    var sources: ((AppSettings) -> SourceRegistry)? by singleProvider()
    var seed: suspend (NewsStore, AppSettings) -> Unit = { _, _ -> }
    var directory: ((NewsStore, DirectoryNetwork?) -> (suspend (DirectoryProvider, String, Int) -> DirectoryResults))? by singleProvider()
    var filterOptions: (List<SourceEntity>, FeedCategory) -> List<FeedSourceOption> = { sources, category ->
        sources.filter { it.enabled && (category == FeedCategory.ALL || runCatching {
            val tags = org.json.JSONArray(it.categoriesJson)
            (0 until tags.length()).any { index -> tags.getString(index) == category.name }
        }.getOrDefault(false)) }.map { FeedSourceOption("source:${it.id}", it.name, listOf(it.id)) }
    }
    var article: ((AppSettings, ArticleExtractor?) -> ArticleLoader)? by singleProvider()
    var extractor: ((AppSettings) -> ArticleExtractor)? by singleProvider()
    var generate: (suspend (List<ArticleEntity>, String, AppSettings) -> DigestEntity)? by singleProvider()
    var refresh: ((NewsStore, NewsCollector, AppSettings, () -> Long, FeedLoader) -> (suspend () -> RefreshResult))? by singleProvider()
    var prefetch: ((PrefetchArticleStore, AppSettings, Context) -> (suspend () -> io.newsbrief.app.sync.PrefetchOutcome))? by singleProvider()
    var start: (Context) -> Unit = {}
    var feedUpdated: (Context) -> Unit = {}
    var images: ((Context) -> PrefetchImageStore)? by singleProvider()
    var presentation: ArticlePresentation? by singleProvider()
    var browser: (@Composable (String, String, Boolean) -> Unit)? by singleProvider()
    companion object {
        fun create(features: List<AppFeature>): FeatureBindings = FeatureBindings().also { b ->
            features.sortedBy { it.order }.forEach { it.configure(b) }
        }
    }
}
