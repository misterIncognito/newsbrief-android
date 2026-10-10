package io.newsbrief.app

import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.newsbrief.app.ui.NewsViewModel
import io.newsbrief.app.network.NewsRouting
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in smoke checks against existing phone data and real public publishers. */
@RunWith(AndroidJUnit4::class)
class TelegramReaderLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openFullArticle(postUrl: String, host: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyArticleReaders") == "true")
        val app = compose.activity.application as NewsBriefApp
        val post = runBlocking { app.repository.lookup(postUrl) }
        assumeTrue("The smoke-test post must already exist on this phone", post != null)
        val vm = ViewModelProvider(compose.activity)[NewsViewModel::class.java]
        compose.runOnUiThread { vm.openReader(post!!) }
        compose.waitUntil(40_000) { vm.reader.value != null && !vm.readerBusy.value }
        assertNull(vm.reader.value!!.notice)
        val label = "Читать полную статью · $host"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(40_000) {
            vm.readerSource.value?.link != postUrl && vm.reader.value != null && !vm.readerBusy.value
        }
        val article = vm.reader.value!!
        assertNull(article.notice)
        assertTrue("Full article text should be available", article.text.length > 500)
        assertTrue(article.originalUrl.startsWith("https://$host/"))
        assertTrue(article.images.isNotEmpty())
        if (host.startsWith("zerkalo-")) assertTrue(NewsRouting.requiresProxy(article.originalUrl))
        compose.onNodeWithText("Открыть во встроенном браузере").performScrollTo().performClick()
        compose.waitUntil(40_000) {
            var loaded = false
            compose.runOnUiThread {
                val browser = findBrowser(compose.activity.window.decorView)
                loaded = browser?.progress == 100 && browser.title.orEmpty().contains(article.title.take(30))
            }
            loaded
        }
        compose.onNodeWithText("← К статье").performClick()
        compose.waitUntil(10_000) { vm.browserUrl.value == null }
        compose.onNodeWithText("← Назад").performClick()
        compose.waitUntil(10_000) { vm.readerSource.value?.link == postUrl && vm.reader.value != null }
        compose.onNodeWithText(label).assertExists()
    }

    private fun findBrowser(view: android.view.View): android.webkit.WebView? {
        if (view is android.webkit.WebView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
            findBrowser(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test fun devbyTelegramFullArticleOpensInsideNativeReader() {
        openFullArticle("https://t.me/devby/16806", "devby.io")
    }
    @Test fun zerkaloTelegramMirrorFullArticleOpensThroughWorker() {
        openFullArticle("https://t.me/zerkalo_io/176330", "zerkalo-6oct.site")
    }
}
