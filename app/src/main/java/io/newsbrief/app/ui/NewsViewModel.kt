package io.newsbrief.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.NewsBriefApp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ReaderArticle
import io.newsbrief.app.data.DigestEntity
import io.newsbrief.app.data.DigestMode
import io.newsbrief.app.data.AppSettings
import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.data.FeedCategory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

class NewsViewModel @JvmOverloads constructor(
    application: Application,
    private val repo: io.newsbrief.app.data.NewsRepository = (application as NewsBriefApp).repository,
    private val store: AppSettings = (application as NewsBriefApp).settings,
    val features: List<io.newsbrief.app.runtime.AppFeature> = (application as? NewsBriefApp)?.runtime?.features ?: io.newsbrief.app.runtime.GeneratedFeatures.create()
) : AndroidViewModel(application), SourcesUi, ReaderUi, FeedUi, AiUi {

    val bindings = (application as? NewsBriefApp)?.runtime?.bindings ?: io.newsbrief.app.runtime.FeatureBindings.create(features)
    val presentation = bindings.presentation
    override val browserAvailable get() = bindings.browser != null
    fun openArticle(article: ArticleEntity) {
        if (presentation != null) openReader(article)
        else if (browserAvailable) openBrowser(article.link, article.proxyRequired)
        else openExternalArticle(getApplication(), article.link)
    }
    override val sourcePlugins = repo.sourcePlugins.available
    private val _telegramProxyUrl = MutableStateFlow(store.telegramProxyUrl())
    val telegramProxyUrl = _telegramProxyUrl.asStateFlow()
    private val _hasTelegramProxyKey = MutableStateFlow(store.hasTelegramProxyKey())
    val hasTelegramProxyKey = _hasTelegramProxyKey.asStateFlow()
    private val _proxyChannels = MutableStateFlow(ProxyChannelsState())
    override val proxyChannels = _proxyChannels.asStateFlow()
    private var proxyChannelsJob: Job? = null
    override fun closeProxyChannels() { proxyChannelsJob?.cancel(); _proxyChannels.value = ProxyChannelsState() }
    override fun openProxyChannels() {
        proxyChannelsJob?.cancel()
        _proxyChannels.value = ProxyChannelsState(open = true, busy = true)
        proxyChannelsJob = viewModelScope.launch {
            try {
                val channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repo.sourcePlugins.plugin(SourceKind.TELEGRAM_PROXY).discover()
                }
                _proxyChannels.value = ProxyChannelsState(open = true, channels = channels)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _proxyChannels.value = ProxyChannelsState(open = true,
                error = "Не удалось загрузить каналы: ${e.message.orEmpty().take(120)}") }
        }
    }
    fun saveTelegramProxy(url: String, key: String) {
        try {
            store.setTelegramProxyUrl(url)
            if (key.isNotBlank()) store.saveTelegramProxyKey(key)
            _telegramProxyUrl.value = store.telegramProxyUrl()
            _hasTelegramProxyKey.value = store.hasTelegramProxyKey()
            report("Настройки Telegram-прокси сохранены")
        } catch (e: Exception) { report("Не удалось сохранить Telegram-прокси: ${e.message}") }
    }
    fun removeTelegramProxyKey() {
        store.saveTelegramProxyKey(""); _hasTelegramProxyKey.value = false
        report("Ключ Telegram-прокси удалён")
    }

    override val sources = repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    override val articles = repo.observeLatest().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    override val savedUrls = repo.observeSavedUrls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _savedOnly = MutableStateFlow(false)
    override val savedOnly = _savedOnly.asStateFlow()
    override fun showSaved(value: Boolean) { if (bindings.saveArticle != null) _savedOnly.value = value }
    private val saving = mutableSetOf<String>()
    override fun toggleSaved(article: ArticleEntity) = viewModelScope.launch {
        if (!saving.add(article.link)) return@launch
        try {
            repo.toggleSaved(article)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { report("Не удалось выполнить действие: ${e.message}") }
        finally { saving.remove(article.link) }
    }

    private val _feedCategory = MutableStateFlow(FeedCategory.ALL)
    override val feedCategory = _feedCategory.asStateFlow()
    private val _feedSources = MutableStateFlow<Set<String>>(emptySet())
    override val feedSources = _feedSources.asStateFlow()
    override val feedOptions = combine(repo.observeSources(), _feedCategory) { sources, category ->
        repo.feedOptions(sources, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val feedArticles = combine(repo.observeSources(), _feedCategory, _feedSources, _savedOnly) { sources, category, source, saved ->
        saved to repo.filterSourceIds(sources, category, source)
    }.distinctUntilChanged().flatMapLatest { (saved, ids) -> if (saved) repo.observeSaved() else repo.observeFeed(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override fun filterCategory(category: FeedCategory) {
        _feedSources.value = emptySet()
        _feedCategory.value = category
    }
    override fun filterSource(key: String?) { _feedSources.value = key?.let { setOf(it) }.orEmpty() }
    override fun toggleFeedSource(key: String) {
        val current = _feedSources.value
        _feedSources.value = if (key in current) current - key else current + key
    }
    override fun clearFeedFilter() { filterCategory(FeedCategory.ALL) }

    private val _mode = MutableStateFlow(DigestMode.SHORT)
    override val mode = _mode.asStateFlow()
    private val _digest = MutableStateFlow<DigestEntity?>(null)
    override val digest = _digest.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    override val refreshing = _refreshing.asStateFlow()
    private val _generating = MutableStateFlow(false)
    override val generating = _generating.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _config = MutableStateFlow(store.read())
    val config = _config.asStateFlow()
    private val _hasKey = MutableStateFlow(store.hasApiKey(store.read().provider))
    override val hasKey = _hasKey.asStateFlow()
    private val _workerUrl = MutableStateFlow(store.workerUrl())
    override val workerUrl = _workerUrl.asStateFlow()
    private val _readerSource = MutableStateFlow<ArticleEntity?>(null)
    override val readerSource = _readerSource.asStateFlow()
    private val _browserUrl = MutableStateFlow<String?>(null)
    val browserUrl = _browserUrl.asStateFlow()
    private val _browserForceProxy = MutableStateFlow(false)
    val browserForceProxy = _browserForceProxy.asStateFlow()
    override fun openBrowser(url: String, forceProxy: Boolean) {
        if (io.newsbrief.app.network.ArticleLinks.valid(url)) {
            if (!browserAvailable) { openExternalArticle(getApplication(), url); return }
            _browserForceProxy.value = forceProxy
            _browserUrl.value = url
        }
    }
    fun closeBrowser() { _browserUrl.value = null }
    private val _reader = MutableStateFlow<ReaderArticle?>(null)
    override val reader = _reader.asStateFlow()
    private val _readerBusy = MutableStateFlow(false)
    override val readerBusy = _readerBusy.asStateFlow()

    private val _catalogOpen = MutableStateFlow(false)
    val catalogOpen = _catalogOpen.asStateFlow()
    private val _directoryState = MutableStateFlow(DirectoryState())
    override val directoryState = _directoryState.asStateFlow()
    private var directoryJob: Job? = null
    private var directoryGeneration = 0

    override fun openCatalog() {
        _catalogOpen.value = true
        if (_directoryState.value.results == null) searchDirectory()
    }
    fun closeCatalog() {
        _catalogOpen.value = false
        directoryGeneration++
        directoryJob?.cancel()
        _directoryState.value = _directoryState.value.copy(busy = false)
    }
    override fun directoryQuery(query: String) {
        _directoryState.value = _directoryState.value.copy(query = query.take(120))
        if (_directoryState.value.provider == io.newsbrief.app.network.DirectoryProvider.CURATED) searchDirectory()
    }
    override fun directoryProvider(provider: io.newsbrief.app.network.DirectoryProvider) {
        _directoryState.value = _directoryState.value.copy(provider = provider, results = null, error = null)
        searchDirectory()
    }
    override fun searchDirectory() {
        directoryJob?.cancel()
        val generation = ++directoryGeneration
        val request = _directoryState.value
        _directoryState.value = request.copy(busy = true, results = null, error = null)
        directoryJob = viewModelScope.launch {
            try {
                val result = repo.discover(request.provider, request.query)
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    results = result, searchedQuery = request.query.trim(), busy = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    busy = false, error = e.message ?: "Каталог недоступен")
            }
        }
    }
    override fun hideDirectoryPromotions(value: Boolean) {
        _directoryState.value = _directoryState.value.copy(hidePromotions = value)
    }
    override fun loadMoreDirectory() {
        val request = _directoryState.value
        val previous = request.results ?: return
        val offset = previous.nextOffset ?: return
        if (request.busy || request.query.trim() != request.searchedQuery) return
        directoryJob?.cancel()
        val generation = ++directoryGeneration
        _directoryState.value = request.copy(busy = true, error = null)
        directoryJob = viewModelScope.launch {
            try {
                val next = repo.discover(request.provider, request.searchedQuery, offset)
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    results = io.newsbrief.app.network.TelegramTopics.merge(previous, next), busy = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    busy = false, error = e.message ?: "Не удалось загрузить следующую страницу")
            }
        }
    }
    override fun addDirectorySource(source: io.newsbrief.app.network.DirectorySource) {
        if (_directoryState.value.addingAddress != null) return
        _directoryState.value = _directoryState.value.copy(addingAddress = source.address, addMessage = null)
        viewModelScope.launch {
            try {
                val added = repo.addDiscoveredSource(source)
                _directoryState.value = _directoryState.value.copy(addMessage =
                    if (added) "${source.title}: источник добавлен, публикации загружены" else "Источник уже добавлен")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _directoryState.value = _directoryState.value.copy(addMessage = "Не удалось добавить ${source.title}: ${e.message}")
            } finally { _directoryState.value = _directoryState.value.copy(addingAddress = null) }
        }
    }

    init {
        viewModelScope.launch {
            repo.seedDefaults()
            loadDigest()
            if (!_refreshing.value) refresh()
        }
    }

    fun clearMessage() { _message.value = null }
    private fun report(message: String) { _message.value = message }

    private var refreshRequested = false

    fun feedBecameVisible() {
        if (!_refreshing.value) refresh(quiet = true)
    }

    override fun refresh(quiet: Boolean) {
        // Source changes during a refresh require another pass with the current source list.
        refreshRequested = true
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                while (refreshRequested) {
                    refreshRequested = false
                    val outcome = repo.refresh()
                    if (!quiet && outcome.skipped) report("Обновление уже запускалось. Повтор через ${(outcome.retryAfterMillis + 999) / 1000} сек.")
                    else if (!quiet) report("Новых публикаций: ${outcome.added}" +
                        if (outcome.errors.isNotEmpty()) "\nОшибки: ${outcome.errors.joinToString("; ")}" else "")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                report("Ошибка обновления: ${e.message}")
            } finally { _refreshing.value = false }
        }
    }

    override fun changeMode(newMode: String) {
        _mode.value = newMode
        viewModelScope.launch { loadDigest() }
    }

    private suspend fun loadDigest() {
        _digest.value = repo.digest(_mode.value)
    }

    override fun generate() = viewModelScope.launch {
        if (_generating.value) return@launch
        _generating.value = true
        try {
            _digest.value = repo.generateDigest(_mode.value)
            report("Сводка готова")
        } catch (e: Exception) {
            report("Не удалось создать сводку: ${e.message}")
        } finally { _generating.value = false }
    }

    override fun addSource(name: String, kind: String, address: String) = viewModelScope.launch {
        try {
            val added = repo.addSource(name, kind, address)
            report(if (added) "Источник добавлен" else "Такой источник уже подключён")
            if (added) refresh()
        } catch (e: Exception) { report(e.message ?: "Не удалось добавить источник") }
    }

    override fun toggleSource(id: Long, value: Boolean) = viewModelScope.launch {
        repo.toggleSource(id, value)
        if (value) refresh()
    }

    override fun deleteSource(id: Long) = viewModelScope.launch {
        repo.removeSource(id)
        report("Источник удалён")
    }

    private var readerJob: Job? = null
    private val readerHistory = ArrayDeque<ArticleEntity>()

    override fun openReader(article: ArticleEntity, refresh: Boolean) {
        if (presentation == null) { openExternalArticle(getApplication(), article.link); return }
        if (!refresh && _readerSource.value?.link != article.link) readerHistory.clear()
        readerJob?.cancel()
        _readerSource.value = article
        _reader.value = null
        readerJob = viewModelScope.launch {
            _readerBusy.value = true
            try {
                val result = repo.readArticle(article, refresh)
                if (_readerSource.value?.link == article.link) _reader.value = result
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                report("Ошибка чтения: ${e.message}")
            } finally { if (currentCoroutineContext().isActive) _readerBusy.value = false }
        }
    }

    override fun openLink(url: String, sourceName: String, title: String) = viewModelScope.launch {
        val article = repo.lookup(url) ?: ArticleEntity(sourceId = 0, sourceName = sourceName,
            title = title, excerpt = "", link = url, publishedAt = System.currentTimeMillis())
        openReader(article)
    }

    private val resolvedPostLinks = mutableSetOf<String>()
    override fun resolvePostLinks(post: ArticleEntity) {
        if (!resolvedPostLinks.add(post.link)) return
        viewModelScope.launch {
            try {
                if (repo.readArticle(post).notice != null) resolvedPostLinks.remove(post.link)
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
        openReader(repo.linkedArticle(post, url))
    }

    override fun openReaderLink(url: String) = viewModelScope.launch {
        if (!io.newsbrief.app.network.ArticleLinks.valid(url)) return@launch
        val parent = _readerSource.value ?: return@launch
        val linked = repo.linkedArticle(parent, url)
        if (parent.link == url) return@launch
        val history = readerHistory.toList() + parent
        openReader(linked)
        readerHistory.addAll(history)
    }

    fun closeReader() {
        if (_browserUrl.value != null) { closeBrowser(); return }
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
        }
    }

    fun setWorkerUrl(value: String) {
        try {
            store.setWorkerUrl(value)
            _workerUrl.value = store.workerUrl()
            report("Адрес прокси сохранён")
        } catch (e: Exception) { report("Неверный адрес: ${e.message}") }
    }

    fun setProvider(value: String) {
        store.setProvider(value)
        _config.value = store.read()
        _hasKey.value = store.hasApiKey(value)
    }

    fun setModel(value: String) {
        if (value.isBlank()) { report("Название модели не должно быть пустым"); return }
        store.setModel(_config.value.provider, value)
        _config.value = store.read()
        report("Модель сохранена")
    }

    fun saveKey(value: String) {
        store.saveApiKey(_config.value.provider, value)
        _hasKey.value = store.hasApiKey(_config.value.provider)
        report(if (value.isBlank()) "Ключ удалён" else if (store is io.newsbrief.app.data.MemorySettings) "Ключ сохранён до закрытия приложения" else "Ключ сохранён на устройстве")
    }
}
