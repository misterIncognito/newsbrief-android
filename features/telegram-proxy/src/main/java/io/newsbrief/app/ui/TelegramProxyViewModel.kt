package io.newsbrief.app.ui
import io.newsbrief.app.util.failureMessage
import io.newsbrief.modules.features.telegram_proxy.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TelegramProxyViewModel(private val host: FeatureHost) : ViewModel(), TelegramProxyUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    private val _telegramProxyUrl = MutableStateFlow(store.telegramProxyUrl())
    override val telegramProxyUrl = _telegramProxyUrl.asStateFlow()
    private val _hasTelegramProxyKey = MutableStateFlow(store.hasTelegramProxyKey())
    override val hasTelegramProxyKey = _hasTelegramProxyKey.asStateFlow()
    private val _proxyChannels = MutableStateFlow(ProxyChannelsState())
    override val proxyChannels = _proxyChannels.asStateFlow()
    private var proxyChannelsJob: Job? = null
    override fun closeProxyChannels() { proxyChannelsJob?.cancel(); _proxyChannels.value = ProxyChannelsState() }
    override fun openProxyChannels() {
        proxyChannelsJob?.cancel()
        _proxyChannels.value = ProxyChannelsState(open = true, busy = true)
        proxyChannelsJob = viewModelScope.launch {
            try {
                val channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    services.sources.plugin(SourceKind.TELEGRAM_PROXY).discover()
                }
                _proxyChannels.value = ProxyChannelsState(open = true, channels = channels)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _proxyChannels.value = ProxyChannelsState(open = true,
                error = host.context.getString(R.string.nb_tg_channels_error, host.context.failureMessage(e).take(120))) }
        }
    }
    override fun saveTelegramProxy(url: String, key: String) {
        try {
            store.setTelegramProxyUrl(url)
            if (key.isNotBlank()) store.saveTelegramProxyKey(key)
            _telegramProxyUrl.value = store.telegramProxyUrl()
            _hasTelegramProxyKey.value = store.hasTelegramProxyKey()
            report(host.context.getString(R.string.nb_tg_saved))
        } catch (e: Exception) { report(host.context.getString(R.string.nb_tg_save_error, host.context.failureMessage(e))) }
    }
    override fun removeTelegramProxyKey() {
        store.saveTelegramProxyKey(""); _hasTelegramProxyKey.value = false
        report(host.context.getString(R.string.nb_tg_key_removed))
    }

    override val sources = services.store.sourceDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private suspend fun refreshFeed() { if (!host.command("refresh")) services.refresh() }
    override fun addSource(name: String, kind: String, address: String) = viewModelScope.launch {
        try {
            val added = checkNotNull(services.sourceActions) { host.context.getString(io.newsbrief.modules.core.api.R.string.nb_sources_missing) }.add(name, kind, address)
            report(if (added) host.context.getString(io.newsbrief.modules.core.api.R.string.nb_source_added) else host.context.getString(io.newsbrief.modules.core.api.R.string.nb_source_exists))
            if (added) refreshFeed()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { report(host.context.failureMessage(e).takeIf { it.isNotBlank() } ?: host.context.getString(io.newsbrief.modules.core.api.R.string.nb_source_failed)) }
    }


}
