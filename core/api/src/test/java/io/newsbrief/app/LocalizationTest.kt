package io.newsbrief.app

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.newsbrief.modules.core.api.R
import io.newsbrief.app.util.FeedRules
import io.newsbrief.app.util.failureMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocalizationTest {
    private fun contextFor(language: String): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    @Test fun englishAndRussianStringsFollowConfiguration() {
        assertEquals("Settings", contextFor("en").getString(R.string.nb_text_005))
        assertEquals("Настройки", contextFor("ru").getString(R.string.nb_text_005))
        assertEquals("Selected: 3", contextFor("en").getString(R.string.nb_selected_count, 3))
        assertEquals("Выбрано: 3", contextFor("ru").getString(R.string.nb_selected_count, 3))
    }

    @Test fun sourceValidationFailureUsesTheRequestedLanguage() {
        val failure = runCatching { FeedRules.canonicalAddress("http://example.com/feed", "RSS") }.exceptionOrNull()!!
        assertEquals("A complete HTTPS feed URL is required", contextFor("en").failureMessage(failure))
        assertEquals("Нужна полная HTTPS-ссылка на RSS", contextFor("ru").failureMessage(failure))
    }

    @Test fun formattedFailureUsesArgumentsInBothLanguages() {
        val failure = io.newsbrief.app.util.LocalizedFailure(R.string.nb_err_tg_http, "fallback", 503)
        assertEquals("Telegram proxy: HTTP 503", contextFor("en").failureMessage(failure))
        assertEquals("Telegram-прокси: HTTP 503", contextFor("ru").failureMessage(failure))
    }

    @Test fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals("Settings", contextFor("fr").getString(R.string.nb_text_005))
    }
}
