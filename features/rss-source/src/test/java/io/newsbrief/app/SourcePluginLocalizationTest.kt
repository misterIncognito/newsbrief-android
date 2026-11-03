package io.newsbrief.app

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.network.RssSourcePlugin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SourcePluginLocalizationTest {
    @Test fun rssHintFollowsTheAppConfiguration() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        fun context(language: String): Context = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        )
        val info = RssSourcePlugin { "" }.info
        assertEquals("HTTPS feed URL for RSS or Atom", info.addressHint(context("en")))
        assertEquals("HTTPS-ссылка на RSS или Atom", info.addressHint(context("ru")))
    }
}
