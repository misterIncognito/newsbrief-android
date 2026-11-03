package io.newsbrief.app.ui

import io.newsbrief.app.data.SourceEntity
import io.newsbrief.app.network.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job

data class ProxyChannelsState(val open: Boolean = false, val busy: Boolean = false,
        val channels: List<io.newsbrief.app.data.SourceEntity> = emptyList(), val error: String? = null)

data class DirectoryState(
        val provider: io.newsbrief.app.network.DirectoryProvider = io.newsbrief.app.network.DirectoryProvider.RSS,
        val query: String = "technology", val searchedQuery: String = "",
        val results: io.newsbrief.app.network.DirectoryResults? = null, val busy: Boolean = false,
        val error: String? = null, val addingAddress: String? = null, val addMessage: String? = null,
        val hidePromotions: Boolean = true
    )

interface SourcesUi {
    val sourcePlugins: List<SourcePluginInfo>
    val sources: StateFlow<List<SourceEntity>>
    fun addSource(name: String, kind: String, address: String): Job
    fun toggleSource(id: Long, value: Boolean): Job
    fun deleteSource(id: Long): Job
}
