package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class TelegramProxyFeature : AppFeature {
    override val hasSettings = true
    override val id = "telegram-proxy"
    override val title = "Telegram-прокси"
    override val order = 25
    override fun configure(bindings: FeatureBindings) {
        bindings.sourcePlugins += { settings -> TelegramProxySourcePlugin(TelegramProxyClient {
            TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }) }
        bindings.telegramPlugins += { settings -> TelegramProxyContent(TelegramProxyClient {
            TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }) }
    }
    @Composable override fun Screen(vm: AppUi) { TelegramProxyChannelsPage(vm) }
    @Composable override fun Settings(vm: AppUi) { TelegramSettingsSection(vm) }
}
