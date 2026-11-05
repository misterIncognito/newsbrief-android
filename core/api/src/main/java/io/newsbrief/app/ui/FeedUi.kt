package io.newsbrief.app.ui
import io.newsbrief.app.data.*
import kotlinx.coroutines.flow.StateFlow
interface FeedUi {
    val feedLimit: StateFlow<Int>
    fun loadMoreFeed()
    val searchOpen: StateFlow<Boolean>
    fun toggleNewsSearch()
    val searchQuery: StateFlow<String>
    fun searchNews(query: String)
    val unreadCount: StateFlow<Int>
    val feedArticles: StateFlow<List<ArticleEntity>>
    val sources: StateFlow<List<SourceEntity>>
    val refreshing: StateFlow<Boolean>
    val savedOnly: StateFlow<Boolean>
    val filtered: StateFlow<Boolean>
    fun refresh(quiet: Boolean = false)
    fun feedBecameVisible()
}
