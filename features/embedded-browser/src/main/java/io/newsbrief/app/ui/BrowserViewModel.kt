package io.newsbrief.app.ui
import io.newsbrief.modules.core.api.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BrowserViewModel(private val host: FeatureHost) : ViewModel(), BrowserUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    private val _browserUrl = MutableStateFlow<String?>(null)
    override val browserUrl = _browserUrl.asStateFlow()
    private val _browserForceProxy = MutableStateFlow(false)
    override val browserForceProxy = _browserForceProxy.asStateFlow()
    override fun openBrowser(url: String, forceProxy: Boolean) {
        if (io.newsbrief.app.network.ArticleLinks.valid(url)) {
            _browserForceProxy.value = forceProxy
            _browserUrl.value = url
            host.navigator.show("browser", host.context.getString(R.string.nb_browser_back), ::closeBrowser) {
                bindings.browser?.invoke(url, store.workerUrl(), forceProxy)
            }
        }
    }
    override fun closeBrowser() { _browserUrl.value = null; host.navigator.dismiss("browser") }

}
