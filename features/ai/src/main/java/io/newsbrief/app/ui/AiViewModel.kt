package io.newsbrief.app.ui
import io.newsbrief.app.util.failureMessage
import io.newsbrief.modules.core.api.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AiViewModel(private val host: FeatureHost) : ViewModel(), AiUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    private val _mode = MutableStateFlow(DigestMode.SHORT)
    override val mode = _mode.asStateFlow()
    private val _digest = MutableStateFlow<DigestEntity?>(null)
    override val digest = _digest.asStateFlow()
    private val _generating = MutableStateFlow(false)
    override val generating = _generating.asStateFlow()
    private val _config = MutableStateFlow(store.read())
    override val config = _config.asStateFlow()
    private val _hasKey = MutableStateFlow(store.hasApiKey(store.read().provider))
    override val hasKey = _hasKey.asStateFlow()
    override val articles = services.store.articleDao().observeRecent(0).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    override val sources = services.store.sourceDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { viewModelScope.launch { loadDigest() } }
    override fun changeMode(newMode: String) {
        _mode.value = newMode
        viewModelScope.launch { loadDigest() }
    }

    private suspend fun loadDigest() {
        _digest.value = services.store.digestDao().find(java.time.LocalDate.now().toString(), _mode.value)
    }

    override fun generate() = viewModelScope.launch {
        if (_generating.value) return@launch
        _generating.value = true
        try {
            _digest.value = generateDigest(_mode.value)
            report(host.context.getString(R.string.nb_digest_ready))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            report(host.context.getString(R.string.nb_digest_failed, host.context.failureMessage(e)))
        } finally { _generating.value = false }
    }

    private suspend fun generateDigest(mode: String): DigestEntity {
        require(mode == DigestMode.SHORT || mode == DigestMode.FULL)
        val recent = services.store.articleDao().recent(System.currentTimeMillis() - 24L * 60 * 60_000, 600)
        val digest = checkNotNull(bindings.generate)(recent, mode, store)
        services.store.digestDao().save(digest)
        return digest
    }
    override fun openLink(url: String, sourceName: String, title: String) = viewModelScope.launch {
        val article = services.store.articleDao().findByLink(url) ?: ArticleEntity(sourceId = 0, sourceName = sourceName,
            title = title, excerpt = "", link = url, publishedAt = System.currentTimeMillis())
        host.openArticle(article)
    }
    override fun setProvider(value: String) {
        store.setProvider(value)
        _config.value = store.read()
        _hasKey.value = store.hasApiKey(value)
    }

    override fun setModel(value: String) {
        if (value.isBlank()) { report(host.context.getString(R.string.nb_model_required)); return }
        store.setModel(_config.value.provider, value)
        _config.value = store.read()
        report(host.context.getString(R.string.nb_model_saved))
    }

    override fun saveKey(value: String) {
        store.saveApiKey(_config.value.provider, value)
        _hasKey.value = store.hasApiKey(_config.value.provider)
        report(if (value.isBlank()) host.context.getString(R.string.nb_key_deleted) else if (bindings.persistentStorage == null) host.context.getString(R.string.nb_key_temporary) else host.context.getString(R.string.nb_key_saved))
    }
}
