package io.newsbrief.app.features

import android.content.Context
import io.newsbrief.modules.features.telegram_proxy.R

import androidx.compose.runtime.Composable
import io.newsbrief.app.network.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.*

class TelegramProxyFeature : AppFeature {
    override val hasSettings = true
    override val id = "telegram-proxy"
    override fun localizedTitle(context: Context): String = context.getString(R.string.nb_feature_telegram_proxy)
    override val title = "Telegram-прокси"
    override val order = 25
    override fun configure(bindings: FeatureBindings) {
        bindings.controller(TelegramProxyUi::class.java, ::TelegramProxyViewModel)
        bindings.sourcePlugins += { settings -> TelegramProxySourcePlugin(TelegramProxyClient {
            TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }) }
        bindings.telegramPlugins += { settings -> TelegramProxyContent(TelegramProxyClient {
            TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }) }
    }
    @Composable override fun Screen(host: FeatureHost) { TelegramProxyChannelsPage(host.controller(TelegramProxyUi::class.java)) }
    @Composable override fun Settings(host: FeatureHost) { TelegramSettingsSection(host.controller(TelegramProxyUi::class.java)) }
}
