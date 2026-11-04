package io.newsbrief.app.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FeedFiltersViewModel(private val host: FeatureHost) : ViewModel(), FeedFilterUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    private val _feedCategory = MutableStateFlow(FeedCategory.ALL)
    override val feedCategory = _feedCategory.asStateFlow()
    private val _feedSources = MutableStateFlow<Set<String>>(emptySet())
    override val feedSources = _feedSources.asStateFlow()
    override val availableCategories = services.store.sourceDao().observe()
        .map { FeedFilters.availableCategories(it) }
        .onEach { categories ->
            if (_feedCategory.value !in categories) {
                _feedCategory.value = FeedCategory.ALL
                _feedSources.value = emptySet()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf(FeedCategory.ALL))
    override val feedOptions = combine(services.store.sourceDao().observe(), _feedCategory, services.store.articleDao().observeSourceArticleCounts()) { sources, category, counts ->
        FeedFilters.withArticleCounts(bindings.filterOptions(sources, category), counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override val selection = combine(_feedCategory, _feedSources) { category, keys -> FeedSelection(category, keys) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSelection())

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


}
