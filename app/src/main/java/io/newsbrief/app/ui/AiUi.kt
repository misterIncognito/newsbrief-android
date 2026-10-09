package io.newsbrief.app.ui

import io.newsbrief.app.data.DigestEntity
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SourceEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

interface AiUi {
    val articles: StateFlow<List<ArticleEntity>>
    val sources: StateFlow<List<SourceEntity>>
    val hasKey: StateFlow<Boolean>
    val mode: StateFlow<String>
    val digest: StateFlow<DigestEntity?>
    val generating: StateFlow<Boolean>
    fun changeMode(newMode: String)
    fun generate(): Job
    fun openLink(url: String, sourceName: String, title: String): Job
}
