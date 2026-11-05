package io.newsbrief.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.runtime.FeatureHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReadStatusViewModel(private val host: FeatureHost) : ViewModel(), ReadStatusUi {
    private val _unreadOnly = MutableStateFlow(false)
    override val unreadOnly = _unreadOnly.asStateFlow()
    private val _visibleReadIds = MutableStateFlow<Set<Long>>(emptySet())
    private var manuallyChangedVisible = emptySet<Long>()
    override val visibleReadIds = _visibleReadIds.asStateFlow()
    override fun setUnreadOnly(value: Boolean) {
        if (_unreadOnly.value != value) _visibleReadIds.value = emptySet()
        _unreadOnly.value = value
    }
    override fun clearUnreadSession() { _visibleReadIds.value = emptySet() }
    override fun toggleRead(article: ArticleEntity): kotlinx.coroutines.Job {
        manuallyChangedVisible = manuallyChangedVisible + article.id
        if (_unreadOnly.value && article.readAt == null) _visibleReadIds.value = _visibleReadIds.value + article.id
        return viewModelScope.launch {
            host.services.store.articleDao().setReadAt(article.link, if (article.readAt == null) System.currentTimeMillis() else null)
        }
    }
    override fun retainVisible(ids: List<Long>) {
        manuallyChangedVisible = manuallyChangedVisible.intersect(ids.toSet())
    }
    override suspend fun markVisible(ids: List<Long>) {
        val automaticIds = ids.filterNot { it in manuallyChangedVisible }
        if (automaticIds.isNotEmpty()) {
            // Keep read items in this feed session even after they leave the viewport.
            if (_unreadOnly.value) _visibleReadIds.value = _visibleReadIds.value + automaticIds
            host.services.store.articleDao().markVisibleRead(automaticIds, System.currentTimeMillis())
        }
    }
}
