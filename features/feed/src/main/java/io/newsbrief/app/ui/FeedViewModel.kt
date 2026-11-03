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

class FeedViewModel(private val host: FeatureHost) : ViewModel(), FeedUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    override val sources = services.store.sourceDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val readStatus = host.optional(ReadStatusUi::class.java)
    private val unreadOnly = readStatus?.unreadOnly ?: flowOf(false)
    private val pinnedReadIds = readStatus?.visibleReadIds ?: flowOf(emptySet())
    private val query = host.optional(FeedFilterUi::class.java)?.selection ?: flowOf(FeedSelection())
    override val savedOnly = (host.optional(SavedUi::class.java)?.savedOnly ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    override val filtered = combine(query, unreadOnly) { selection, unread ->
        unread || selection.category != FeedCategory.ALL || selection.keys.isNotEmpty()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    @OptIn(ExperimentalCoroutinesApi::class)
    override val feedArticles = combine(sources, query, savedOnly, unreadOnly, pinnedReadIds) { sources, query, saved, unread, pinned ->
        val ids = if (query.category == FeedCategory.ALL && query.keys.isEmpty()) null else
            bindings.filterOptions(sources, query.category).filter { query.keys.isEmpty() || it.key in query.keys }
                .flatMap { it.ids }.distinct().sorted()
        FeedRequest(saved, ids, unread, pinned)
    }.distinctUntilChanged().flatMapLatest { (saved, ids, unread, pinned) ->
        if (saved) services.store.articleDao().observeSaved()
        else if (unread) services.store.articleDao().observeUnreadFiltered(ids.orEmpty(), ids != null, pinned.toList())
        else services.store.articleDao().observeFiltered(ids.orEmpty(), ids != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private data class FeedRequest(val saved: Boolean, val ids: List<Long>?, val unread: Boolean, val pinned: Set<Long>)
    private val _refreshing = MutableStateFlow(false)
    override val refreshing = _refreshing.asStateFlow()
    private var refreshRequested = false
    init { refresh() }

    override fun feedBecameVisible() {
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
                    val outcome = services.refresh()
                    if (!quiet && outcome.skipped) report(host.context.getString(R.string.nb_feed_retry, (outcome.retryAfterMillis + 999) / 1000))
                    else if (!quiet) report(host.context.getString(R.string.nb_feed_new, outcome.added) +
                        if (outcome.errors.isNotEmpty()) host.context.getString(R.string.nb_feed_errors,
                            if (outcome.sourceFailures.size == outcome.errors.size) outcome.sourceFailures.joinToString("; ") { listOf(it.sourceName, host.context.failureMessage(it.error).take(120)).filter { part -> part.isNotBlank() }.joinToString(": ") } else outcome.errors.joinToString("; ")) else "")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                report(host.context.getString(R.string.nb_feed_error, host.context.failureMessage(e)))
            } finally { _refreshing.value = false }
        }
    }


}
