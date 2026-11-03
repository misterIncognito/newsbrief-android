package io.newsbrief.app.ui
import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.failureMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.newsbrief.app.runtime.*
import io.newsbrief.app.data.*
import io.newsbrief.app.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SavedArticlesViewModel(private val host: FeatureHost) : ViewModel(), SavedUi {
    private val services = host.services
    private val store = services.settings
    private val bindings = host.bindings
    private fun report(message: String) = host.report(message)
    override val savedUrls = services.store.savedArticleDao().observeUrls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _savedOnly = MutableStateFlow(false)
    override val savedOnly = _savedOnly.asStateFlow()
    override fun showSaved(value: Boolean) { _savedOnly.value = value }
    private val saving = mutableSetOf<String>()
    override fun toggleSaved(article: ArticleEntity) = viewModelScope.launch {
        if (!saving.add(article.link)) return@launch
        try {
            (bindings.saveArticle ?: error(host.context.getString(R.string.nb_save_unavailable)))(services.store, article)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(host.context.getString(R.string.nb_action_failed, host.context.failureMessage(e))) }
        finally { saving.remove(article.link) }
    }


}
