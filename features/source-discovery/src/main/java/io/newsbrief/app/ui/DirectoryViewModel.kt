package io.newsbrief.app.ui
import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.failureMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DirectoryViewModel(private val host: FeatureHost) : ViewModel(), DirectoryUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    override val sourcePlugins = services.sources.available
    override val sources = services.store.sourceDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _directoryState = MutableStateFlow(DirectoryState())
    override val directoryState = _directoryState.asStateFlow()
    private var directoryJob: Job? = null
    private var directoryGeneration = 0

    override fun onCleared() {
        directoryGeneration++
        directoryJob?.cancel()
        _directoryState.value = _directoryState.value.copy(busy = false)
    }
    override fun directoryQuery(query: String) {
        _directoryState.value = _directoryState.value.copy(query = query.take(120))
        if (_directoryState.value.provider == io.newsbrief.app.network.DirectoryProvider.CURATED) searchDirectory()
    }
    override fun directoryProvider(provider: io.newsbrief.app.network.DirectoryProvider) {
        val current = _directoryState.value
        val query = if (provider != DirectoryProvider.CURATED && current.query.isBlank()) "technology" else current.query
        _directoryState.value = current.copy(provider = provider, query = query, results = null, error = null)
        searchDirectory()
    }
    override fun searchDirectory() {
        directoryJob?.cancel()
        val generation = ++directoryGeneration
        val request = _directoryState.value
        _directoryState.value = request.copy(busy = true, results = null, error = null)
        directoryJob = viewModelScope.launch {
            try {
                val result = checkNotNull(services.directory).invoke(request.provider, request.query, 0)
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    results = result, searchedQuery = request.query.trim(), busy = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    busy = false, error = host.context.failureMessage(e).takeIf { it.isNotBlank() } ?: host.context.getString(R.string.nb_directory_unavailable))
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
                val next = checkNotNull(services.directory).invoke(request.provider, request.searchedQuery, offset)
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    results = io.newsbrief.app.network.TelegramTopics.merge(previous, next), busy = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == directoryGeneration) _directoryState.value = _directoryState.value.copy(
                    busy = false, error = host.context.failureMessage(e).takeIf { it.isNotBlank() } ?: host.context.getString(R.string.nb_directory_more_error))
            }
        }
    }
    override fun addDirectorySource(source: io.newsbrief.app.network.DirectorySource) {
        if (_directoryState.value.addingAddress != null) return
        _directoryState.value = _directoryState.value.copy(addingAddress = source.address, addMessage = null)
        viewModelScope.launch {
            try {
                val added = checkNotNull(services.sourceActions) { host.context.getString(R.string.nb_sources_missing) }.connect(source)
                _directoryState.value = _directoryState.value.copy(addMessage =
                    if (added) host.context.getString(R.string.nb_directory_added, source.title) else host.context.getString(R.string.nb_directory_exists))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _directoryState.value = _directoryState.value.copy(addMessage = host.context.getString(R.string.nb_directory_add_error, source.title, host.context.failureMessage(e)))
            } finally { _directoryState.value = _directoryState.value.copy(addingAddress = null) }
        }
    }


}
