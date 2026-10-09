package io.newsbrief.app

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.newsbrief.app.ui.NewsViewModel
import io.newsbrief.app.network.ArticleLinks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Explicit opt-in: opens an existing saved Warsaw post's public article on the phone. */
@RunWith(AndroidJUnit4::class)
class OriginalPageLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun browser(view: View = compose.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) browser(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun warsawOriginalArticleRendersInsideNewsBrief() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyOriginalPage") == "true")
        val app = compose.activity.application as NewsBriefApp
        val post = runBlocking { app.repository.lookup("https://t.me/thewwarsaw/21641") }
        assumeTrue("The existing Warsaw post must be available", post != null)
        val link = ArticleLinks.fullArticle(ArticleLinks.decode(post!!.linksJson))!!.url
        val vm = ViewModelProvider(compose.activity)[NewsViewModel::class.java]
        compose.runOnUiThread { vm.openBrowser(link, forceProxy = true) }
        compose.onNodeWithText("Оригинальная страница").performClick()
        compose.waitUntil(60_000) {
            var ready = false
            compose.runOnUiThread { ready = browser()?.let { it.progress == 100 && it.title.orEmpty().contains("Warsaw", true) } == true }
            ready
        }
        val body = AtomicReference<String?>(null)
        compose.runOnUiThread {
            val view = browser()!!
            assertTrue(view.settings.javaScriptEnabled)
            assertTrue(view.url.orEmpty().contains("the-warsaw.pl"))
            view.evaluateJavascript("JSON.stringify({title:document.title,characters:document.body.innerText.length,heading:document.querySelector('h1')?.innerText})") { body.set(it) }
        }
        compose.waitUntil(10_000) { body.get() != null }
        val json = org.json.JSONTokener(body.get()).nextValue() as String
        val result = org.json.JSONObject(json)
        assertTrue(result.getInt("characters") > 500)
        assertTrue(result.optString("heading").contains("Варшаве"))
        println("Warsaw original page: " + result.toString())
        compose.onNodeWithText("← К статье").performClick()
    }
}
