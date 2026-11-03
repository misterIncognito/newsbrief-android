package io.newsbrief.app.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.newsbrief.app.data.*
import io.newsbrief.app.ui.openExternalArticle

interface ShellNavigator {
    fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit)
    fun dismiss(id: String)
}
data class NavigationHandler(val route: String, val priority: Int, val open: (FeatureHost, ArticleEntity) -> Unit)

/** Composition services only: no optional feature state or combined UI interface. */
class FeatureHost(val context: Context, val services: FeatureServices, val navigator: ShellNavigator,
    val report: (String) -> Unit) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val bindings get() = services.bindings
    private val constructing = mutableSetOf<Class<*>>()
    fun <T : Any> optional(type: Class<T>): T? {
        val factory = bindings.controllers[type] ?: return null
        val provider = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V {
                check(constructing.add(type)) { "Controller dependency cycle: ${type.name}" }
                try {
                    val model = factory(this@FeatureHost)
                    check(type.isInstance(model)) { "Invalid controller for ${type.name}" }
                    return model as V
                } finally { constructing.remove(type) }
            }
        })
        @Suppress("UNCHECKED_CAST")
        return provider.get(type.name, ViewModel::class.java) as T
    }
    fun <T : Any> controller(type: Class<T>): T = optional(type) ?: error("Feature controller unavailable: ${type.simpleName}")
    fun command(name: String): Boolean = bindings.commands[name]?.let { it(this); true } ?: false
    fun openArticle(article: ArticleEntity) = navigate("article", article)
    fun openBrowser(url: String, forceProxy: Boolean = false) = navigate("browser",
        ArticleEntity(sourceId = 0, sourceName = "", title = "", excerpt = "", link = url,
            publishedAt = System.currentTimeMillis(), proxyRequired = forceProxy))
    private fun navigate(route: String, article: ArticleEntity) {
        if (!io.newsbrief.app.network.ArticleLinks.valid(article.link)) return
        val handler = bindings.navigation.filter { it.route == route }.maxByOrNull { it.priority }
        if (handler != null) handler.open(this, article) else openExternalArticle(context, article.link)
    }
    fun close() = viewModelStore.clear()
}
