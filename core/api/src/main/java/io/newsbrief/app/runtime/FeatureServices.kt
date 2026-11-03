package io.newsbrief.app.runtime

import io.newsbrief.app.util.LocalizedFailure
import io.newsbrief.modules.core.api.R
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*

interface SourceActions {
    suspend fun add(name: String, kind: String, address: String): Boolean
    suspend fun connect(source: DirectorySource): Boolean
}
/** Instantiates registered service factories; implementations and workflows belong to plugins. */
class FeatureServices(val store: NewsStore, val settings: AppSettings,
    collector: NewsCollector? = null, registry: SourceRegistry? = null,
    val directoryNetwork: DirectoryNetwork? = null, reader: ArticleLoader? = null,
    val clock: () -> Long = System::currentTimeMillis, val onFeedUpdated: () -> Unit = {},
    val bindings: FeatureBindings) {
    val sources = registry ?: (collector as? SourceRegistryOwner)?.plugins ?: (collector as? SourceRegistry)
        ?: bindings.sources?.invoke(settings) ?: EmptySourceRegistry()
    val collector = collector ?: sources
    val articleLoader = reader ?: bindings.article?.invoke(settings, bindings.extractor?.invoke(settings))
    val articles = bindings.articleStore?.invoke(this)
    val sourceActions = bindings.sourceActions?.invoke(this)
    val directory = bindings.directory?.invoke(store, directoryNetwork)
    private val loader = bindings.feedLoader?.invoke(this) ?: FeedLoader {
        RefreshResult(0, 0, listOf("Обновление недоступно: загрузчик ленты не подключён."),
            sourceFailures = listOf(SourceFailure("", LocalizedFailure(R.string.nb_err_feed_unavailable, "Обновление недоступно: загрузчик ленты не подключён."))))
    }
    private val refresh = bindings.refresh?.invoke(store, this.collector, settings, clock, loader) ?: loader::refresh
    suspend fun refresh(): RefreshResult { bindings.seed(store, settings); return refresh.invoke() }
}
