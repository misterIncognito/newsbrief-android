package io.newsbrief.app.ui

import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ReaderArticle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

interface ReaderUi {
    val browserAvailable: Boolean get() = true
    val workerUrl: StateFlow<String>
    val savedUrls: StateFlow<List<String>>
    val readerSource: StateFlow<ArticleEntity?>
    val reader: StateFlow<ReaderArticle?>
    val readerBusy: StateFlow<Boolean>
    fun toggleSaved(article: ArticleEntity): Job
    fun openReader(article: ArticleEntity, refresh: Boolean = false)
    fun openReaderLink(url: String): Job
    fun openBrowser(url: String, forceProxy: Boolean = false)
    fun openFeedLink(post: ArticleEntity, url: String): Job
    fun resolvePostLinks(post: ArticleEntity)
}
