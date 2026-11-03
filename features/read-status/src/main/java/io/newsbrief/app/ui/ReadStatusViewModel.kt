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
    override val visibleReadIds = _visibleReadIds.asStateFlow()
    override fun setUnreadOnly(value: Boolean) {
        if (_unreadOnly.value != value) _visibleReadIds.value = emptySet()
        _unreadOnly.value = value
    }
    override fun toggleRead(article: ArticleEntity) = viewModelScope.launch {
        host.services.store.articleDao().setReadAt(article.link, if (article.readAt == null) System.currentTimeMillis() else null)
    }
    override fun retainVisible(ids: List<Long>) {
        if (_unreadOnly.value) _visibleReadIds.value = _visibleReadIds.value.intersect(ids.toSet())
    }
    override suspend fun markVisible(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            // Keep seen items while they remain visible; otherwise the unread list would
            // continuously replace them and mark unseen items as read.
            if (_unreadOnly.value) _visibleReadIds.value = _visibleReadIds.value + ids
            host.services.store.articleDao().markVisibleRead(ids, System.currentTimeMillis())
        }
    }
}
