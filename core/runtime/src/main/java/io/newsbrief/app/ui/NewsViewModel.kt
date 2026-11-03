package io.newsbrief.app.ui
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.AndroidViewModel
import io.newsbrief.app.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shell state only. Feature state and jobs live in the installed module's ViewModel. */
open class NewsViewModel @JvmOverloads constructor(application: Application,
    val services: FeatureServices = (application as FeatureRuntimeOwner).runtime.services,
    val features: List<AppFeature> = (application as FeatureRuntimeOwner).runtime.features
) : AndroidViewModel(application), ShellNavigator {
    data class Overlay(val id: String, val backLabel: String, val onBack: () -> Unit, val content: @Composable () -> Unit)
    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays = _overlays.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val host = FeatureHost(application, services, this) { _message.value = it }
    fun clearMessage() { _message.value = null }
    override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) {
        _overlays.value = _overlays.value.filterNot { it.id == id } + Overlay(id, backLabel, onBack, content)
    }
    override fun dismiss(id: String) { _overlays.value = _overlays.value.filterNot { it.id == id } }
    fun closeFeatures() { host.close(); _overlays.value = emptyList() }
    override fun onCleared() { closeFeatures() }
}
