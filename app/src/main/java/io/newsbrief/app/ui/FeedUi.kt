package io.newsbrief.app.ui

import io.newsbrief.app.data.*
import kotlinx.coroutines.flow.StateFlow

interface FeedUi {
    val feedArticles: StateFlow<List<ArticleEntity>>
    val sources: StateFlow<List<SourceEntity>>
    val feedOptions: StateFlow<List<FeedSourceOption>>
    val feedCategory: StateFlow<FeedCategory>
    val feedSources: StateFlow<Set<String>>
    val refreshing: StateFlow<Boolean>
    val savedOnly: StateFlow<Boolean>
    fun showSaved(value: Boolean)
    fun filterCategory(category: FeedCategory)
    fun filterSource(key: String?)
    fun toggleFeedSource(key: String)
    fun clearFeedFilter()
    fun refresh(quiet: Boolean = false)
}
