package io.newsbrief.app.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SourcesViewModel(private val host: FeatureHost) : ViewModel(), SourcesUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    override val sourcePlugins = services.sources.available
    override val sources = services.store.sourceDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private suspend fun refreshFeed() { if (!host.command("refresh")) services.refresh() }
    override fun addSource(name: String, kind: String, address: String) = viewModelScope.launch {
        try {
            val added = checkNotNull(services.sourceActions) { "Провайдеры источников не подключены" }.add(name, kind, address)
            report(if (added) "Источник добавлен" else "Такой источник уже подключён")
            if (added) refreshFeed()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { report(e.message ?: "Не удалось добавить источник") }
    }

    override fun toggleSource(id: Long, value: Boolean) = viewModelScope.launch {
        services.store.sourceDao().setEnabled(id, value)
        if (value) refreshFeed()
    }

    override fun deleteSource(id: Long) = viewModelScope.launch {
        services.store.sourceDao().delete(id)
        report("Источник удалён")
    }


}
