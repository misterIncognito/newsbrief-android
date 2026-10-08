package io.newsbrief.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.NewsBriefApp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ReaderArticle
import io.newsbrief.app.data.DigestEntity
import io.newsbrief.app.data.DigestMode
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.data.SourceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class NewsViewModel @JvmOverloads constructor(
    application: Application,
    private val repo: io.newsbrief.app.data.NewsRepository = (application as NewsBriefApp).repository,
    private val store: SettingsStore = (application as NewsBriefApp).settings
) : AndroidViewModel(application) {

    val sources = repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val articles = repo.observeLatest().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mode = MutableStateFlow(DigestMode.SHORT)
    val mode = _mode.asStateFlow()
    private val _digest = MutableStateFlow<DigestEntity?>(null)
    val digest = _digest.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating = _generating.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _config = MutableStateFlow(store.read())
    val config = _config.asStateFlow()
    private val _hasKey = MutableStateFlow(store.hasApiKey(store.read().provider))
    val hasKey = _hasKey.asStateFlow()
    private val _workerUrl = MutableStateFlow(store.workerUrl())
    val workerUrl = _workerUrl.asStateFlow()
    private val _readerSource = MutableStateFlow<ArticleEntity?>(null)
    val readerSource = _readerSource.asStateFlow()
    private val _reader = MutableStateFlow<ReaderArticle?>(null)
    val reader = _reader.asStateFlow()
    private val _readerBusy = MutableStateFlow(false)
    val readerBusy = _readerBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repo.seedDefaults()
            loadDigest()
            refresh()
        }
    }

    fun clearMessage() { _message.value = null }
    private fun report(message: String) { _message.value = message }

    private var refreshRequested = false

    fun refresh() {
        // Source changes during a refresh require another pass with the current source list.
        refreshRequested = true
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                while (refreshRequested) {
                    refreshRequested = false
                    val outcome = repo.refresh()
                    report("Новых публикаций: ${outcome.added}" +
                        if (outcome.errors.isNotEmpty()) "\nОшибки: ${outcome.errors.joinToString("; ")}" else "")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                report("Ошибка обновления: ${e.message}")
            } finally { _refreshing.value = false }
        }
    }

    fun changeMode(newMode: String) {
        _mode.value = newMode
        viewModelScope.launch { loadDigest() }
    }

    private suspend fun loadDigest() {
        _digest.value = repo.digest(_mode.value)
    }

    fun generate() = viewModelScope.launch {
        if (_generating.value) return@launch
        _generating.value = true
        try {
            _digest.value = repo.generateDigest(_mode.value)
            report("Сводка готова")
        } catch (e: Exception) {
            report("Не удалось создать сводку: ${e.message}")
        } finally { _generating.value = false }
    }

    fun addSource(name: String, kind: String, address: String) = viewModelScope.launch {
        try {
            val added = repo.addSource(name, kind, address)
            report(if (added) "Источник добавлен" else "Такой источник уже подключён")
            if (added) refresh()
        } catch (e: Exception) { report(e.message ?: "Не удалось добавить источник") }
    }

    fun toggleSource(id: Long, value: Boolean) = viewModelScope.launch {
        repo.toggleSource(id, value)
        if (value) refresh()
    }

    fun deleteSource(id: Long) = viewModelScope.launch {
        repo.removeSource(id)
        report("Источник удалён")
    }

    private var readerJob: Job? = null

    fun openReader(article: ArticleEntity, refresh: Boolean = false) {
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
            } finally { _readerBusy.value = false }
        }
    }

    fun openLink(url: String, sourceName: String, title: String) = viewModelScope.launch {
        val article = repo.lookup(url) ?: ArticleEntity(sourceId = 0, sourceName = sourceName,
            title = title, excerpt = "", link = url, publishedAt = System.currentTimeMillis())
        openReader(article)
    }

    fun closeReader() {
        readerJob?.cancel()
        _readerBusy.value = false
        _readerSource.value = null
        _reader.value = null
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
        report(if (value.isBlank()) "Ключ удалён" else "Ключ сохранён на устройстве")
    }
}
