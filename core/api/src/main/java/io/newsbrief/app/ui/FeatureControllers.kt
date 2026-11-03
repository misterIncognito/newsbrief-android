package io.newsbrief.app.ui

import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/** Each optional controller is registered and owned by its implementing feature. */
interface FeedFilterUi {
    val feedCategory: StateFlow<FeedCategory>
    val availableCategories: StateFlow<List<FeedCategory>>
    val feedSources: StateFlow<Set<String>>
    val feedOptions: StateFlow<List<FeedSourceOption>>
    val selection: StateFlow<FeedSelection>
    fun filterCategory(category: FeedCategory)
    fun filterSource(key: String?)
    fun toggleFeedSource(key: String)
    fun clearFeedFilter()
}
data class FeedSelection(val category: FeedCategory = FeedCategory.ALL, val keys: Set<String> = emptySet())
interface SavedUi {
    val savedUrls: StateFlow<List<String>>
    val savedOnly: StateFlow<Boolean>
    fun showSaved(value: Boolean)
    fun toggleSaved(article: ArticleEntity): Job
}
interface BrowserUi {
    val browserUrl: StateFlow<String?>
    val browserForceProxy: StateFlow<Boolean>
    fun openBrowser(url: String, forceProxy: Boolean = false)
    fun closeBrowser()
}
interface WorkerSettingsUi {
    val workerUrl: StateFlow<String>
    fun setWorkerUrl(value: String)
}
interface TelegramProxyUi {
    val sources: StateFlow<List<SourceEntity>>
    val proxyChannels: StateFlow<ProxyChannelsState>
    val telegramProxyUrl: StateFlow<String>
    val hasTelegramProxyKey: StateFlow<Boolean>
    fun closeProxyChannels()
    fun openProxyChannels()
    fun saveTelegramProxy(url: String, key: String)
    fun removeTelegramProxyKey()
    fun addSource(name: String, kind: String, address: String): Job
}
interface DirectoryUi {
    val sources: StateFlow<List<SourceEntity>>
    val sourcePlugins: List<SourcePluginInfo>
    val directoryState: StateFlow<DirectoryState>
    fun directoryProvider(provider: DirectoryProvider)
    fun directoryQuery(query: String)
    fun searchDirectory()
    fun hideDirectoryPromotions(value: Boolean)
    fun loadMoreDirectory()
    fun addDirectorySource(source: DirectorySource)
}
