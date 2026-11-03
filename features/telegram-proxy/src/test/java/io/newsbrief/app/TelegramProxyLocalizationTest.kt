package io.newsbrief.app

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.network.TelegramProxyClient
import io.newsbrief.app.network.TelegramProxyConfig
import io.newsbrief.app.network.TelegramProxySourcePlugin
import io.newsbrief.modules.features.telegram_proxy.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TelegramProxyLocalizationTest {
    @Test fun proxyLabelsFollowTheAppConfiguration() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        fun context(language: String): Context = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        )
        val info = TelegramProxySourcePlugin(TelegramProxyClient { TelegramProxyConfig("", "") }).info
        assertEquals("Telegram via proxy", info.title(context("en")))
        assertEquals("Telegram через прокси", info.title(context("ru")))
        assertEquals("Search channels", context("en").getString(R.string.nb_tg_search))
        assertEquals("Поиск канала", context("ru").getString(R.string.nb_tg_search))
    }
}
