package io.newsbrief.app.ui
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
                error = "Не удалось загрузить каналы: ${e.message.orEmpty().take(120)}") }
        }
    }
    override fun saveTelegramProxy(url: String, key: String) {
        try {
            store.setTelegramProxyUrl(url)
            if (key.isNotBlank()) store.saveTelegramProxyKey(key)
            _telegramProxyUrl.value = store.telegramProxyUrl()
            _hasTelegramProxyKey.value = store.hasTelegramProxyKey()
            report("Настройки Telegram-прокси сохранены")
        } catch (e: Exception) { report("Не удалось сохранить Telegram-прокси: ${e.message}") }
    }
    override fun removeTelegramProxyKey() {
        store.saveTelegramProxyKey(""); _hasTelegramProxyKey.value = false
        report("Ключ Telegram-прокси удалён")
    }

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


}
