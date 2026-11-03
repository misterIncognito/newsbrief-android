package io.newsbrief.app.ui
import io.newsbrief.app.util.failureMessage
import io.newsbrief.modules.core.api.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WorkerSettingsViewModel(private val host: FeatureHost) : ViewModel(), WorkerSettingsUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    private val _workerUrl = MutableStateFlow(store.workerUrl())
    override val workerUrl = _workerUrl.asStateFlow()
    override fun setWorkerUrl(value: String) {
        try {
            store.setWorkerUrl(value)
            _workerUrl.value = store.workerUrl()
            report(host.context.getString(R.string.nb_proxy_saved))
        } catch (e: Exception) { report(host.context.getString(R.string.nb_proxy_error, host.context.failureMessage(e))) }
    }


}
