package io.newsbrief.app.ui
import io.newsbrief.modules.core.api.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ReaderViewModel(private val host: FeatureHost) : ViewModel(), ReaderUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    override val browserAvailable get() = bindings.browser != null
    override val workerUrl = host.optional(WorkerSettingsUi::class.java)?.workerUrl ?: MutableStateFlow(store.workerUrl()).asStateFlow()
    override fun openBrowser(url: String, forceProxy: Boolean) = host.openBrowser(url, forceProxy)
    private val _readerSource = MutableStateFlow<ArticleEntity?>(null)
    override val readerSource = _readerSource.asStateFlow()
    private val _reader = MutableStateFlow<ReaderArticle?>(null)
    override val reader = _reader.asStateFlow()
    private val _readerBusy = MutableStateFlow(false)
    override val readerBusy = _readerBusy.asStateFlow()

    private var readerJob: Job? = null
    private val readerHistory = ArrayDeque<ArticleEntity>()

    override fun openReader(article: ArticleEntity, refresh: Boolean) {
        if (!refresh && _readerSource.value?.link != article.link) readerHistory.clear()
        readerJob?.cancel()
        _readerSource.value = article
        host.navigator.show("reader", host.context.getString(R.string.nb_reader_back), ::closeReader) { ReaderPage(this, host) }
        _reader.value = null
        readerJob = viewModelScope.launch {
            _readerBusy.value = true
            try {
                val result = checkNotNull(services.articles).readArticle(article, refresh)
                if (_readerSource.value?.link == article.link) _reader.value = result
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                report(host.context.getString(R.string.nb_reader_error, e.message.orEmpty()))
            } finally { if (currentCoroutineContext().isActive) _readerBusy.value = false }
        }
    }

    private val resolvedPostLinks = mutableSetOf<String>()
    override fun resolvePostLinks(post: ArticleEntity) {
        if (!resolvedPostLinks.add(post.link)) return
        viewModelScope.launch {
            try {
                if (checkNotNull(services.articles).readArticle(post).notice != null) resolvedPostLinks.remove(post.link)
            } catch (e: CancellationException) {
                resolvedPostLinks.remove(post.link)
                throw e
            } catch (_: Exception) {
                resolvedPostLinks.remove(post.link)
            }
        }
    }

    override fun openFeedLink(post: ArticleEntity, url: String) = viewModelScope.launch {
        if (!io.newsbrief.app.network.ArticleLinks.valid(url)) return@launch
        openReader(checkNotNull(services.articles).linkedArticle(post, url))
    }

    override fun openReaderLink(url: String) = viewModelScope.launch {
        if (!io.newsbrief.app.network.ArticleLinks.valid(url)) return@launch
        val parent = _readerSource.value ?: return@launch
        val linked = checkNotNull(services.articles).linkedArticle(parent, url)
        if (parent.link == url) return@launch
        val history = readerHistory.toList() + parent
        openReader(linked)
        readerHistory.addAll(history)
    }

    override fun closeReader() {
        readerJob?.cancel()
        _readerBusy.value = false
        val previous = readerHistory.removeLastOrNull()
        if (previous != null) {
            val history = readerHistory.toList()
            openReader(previous)
            readerHistory.addAll(history)
        } else {
            _readerSource.value = null
            _reader.value = null
            host.navigator.dismiss("reader")
        }
    }


}
