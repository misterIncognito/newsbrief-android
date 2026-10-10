package io.newsbrief.app.ui

import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*
import kotlinx.coroutines.flow.StateFlow

/** UI contract implemented by the host, consumed by optional screens and actions. */
interface AppUi : SourcesUi, ReaderUi, FeedUi, AiUi {
    val features: List<AppFeature>
    val bindings: FeatureBindings
    val presentation: ArticlePresentation?
    val telegramProxyUrl: StateFlow<String>
    val hasTelegramProxyKey: StateFlow<Boolean>
    val message: StateFlow<String?>
    val config: StateFlow<AiConfig>
    val browserUrl: StateFlow<String?>
    val browserForceProxy: StateFlow<Boolean>
    val catalogOpen: StateFlow<Boolean>
    fun openArticle(article: ArticleEntity)
    fun feedBecameVisible()
    fun closeReader()
    fun closeBrowser()
    fun closeCatalog()
    fun clearMessage()
    fun setWorkerUrl(value: String)
    fun saveTelegramProxy(url: String, key: String)
    fun removeTelegramProxyKey()
    fun setProvider(value: String)
    fun setModel(value: String)
    fun saveKey(value: String)
}
