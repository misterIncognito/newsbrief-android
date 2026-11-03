package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.ai.R

import androidx.compose.runtime.Composable
import io.newsbrief.app.ai.AiClient
import io.newsbrief.app.data.DigestArticleSelector
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class AiFeature : AppFeature {
    override val hasSettings = true
    override val id = "ai"
    override fun localizedTitle(context: Context): String = context.getString(R.string.nb_feature_ai)
    override val title = "AI summary"
    override val order = 70
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(AiUi::class.java, ::AiViewModel)
        bindings.generate = { articles, mode, settings -> AiClient(settings).generate(DigestArticleSelector.pick(articles), mode) }
    }
    @Composable override fun Screen(host: FeatureHost) { DigestPage(host.controller(AiUi::class.java)) }
    @Composable override fun Settings(host: FeatureHost) { AiSettingsSection(host.controller(AiUi::class.java)) }
}
