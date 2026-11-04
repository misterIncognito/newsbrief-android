package io.newsbrief.app.ui
import io.newsbrief.app.data.*
import kotlinx.coroutines.flow.StateFlow
interface FeedUi {
    val unreadCount: StateFlow<Int>
    val feedArticles: StateFlow<List<ArticleEntity>>
    val sources: StateFlow<List<SourceEntity>>
    val refreshing: StateFlow<Boolean>
    val savedOnly: StateFlow<Boolean>
    val filtered: StateFlow<Boolean>
    fun refresh(quiet: Boolean = false)
    fun feedBecameVisible()
}
