package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.ai.AiClient
import io.newsbrief.app.data.DigestArticleSelector
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class AiFeature : AppFeature {
    override val hasSettings = true
    override val id = "ai"
    override val title = "Главное"
    override val order = 70
    override fun configure(bindings: FeatureBindings) {
        bindings.generate = { articles, mode, settings -> AiClient(settings).generate(DigestArticleSelector.pick(articles), mode) }
    }
    @Composable override fun Screen(vm: AppUi) { DigestPage(vm) }
    @Composable override fun Settings(vm: AppUi) { AiSettingsSection(vm) }
}
