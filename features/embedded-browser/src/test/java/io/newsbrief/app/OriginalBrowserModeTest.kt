package io.newsbrief.app

import android.app.Application
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.newsbrief.app.network.*
import io.newsbrief.app.ui.EmbeddedBrowserPage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalBrowserModeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun browser(view: View = compose.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) browser(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun request(target: String, main: Boolean = true) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(target)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = mutableMapOf<String, String>()
    }
    @Test fun originalWarsawPageUsesNativeTransportAndProtectedNavigationLocksTheSession() {
        val routes = mutableListOf<BrowserRouting.Route>()
        val url = "https://www.the-warsaw.pl/172461/story/"
        compose.setContent { MaterialTheme { EmbeddedBrowserPage(url, NewsRouting.DEFAULT_WORKER, forceProxy = true,
            download = { target, route, _ -> routes += route; DownloadedResource("<p>Page</p>".toByteArray(), "text/html", "UTF-8", target) }) } }
        compose.runOnIdle { assertFalse(browser()!!.settings.javaScriptEnabled) }
        compose.onNodeWithText("Через загрузчик").assertIsSelected()
        compose.onNodeWithText("Оригинал").assertIsNotSelected()
        compose.onNodeWithText("Оригинал").performClick()
        compose.onNodeWithText("Оригинал").assertIsSelected()
        compose.runOnIdle {
            val view = browser()!!
            assertTrue(view.settings.javaScriptEnabled); assertTrue(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowFileAccess); assertFalse(view.settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
            assertNull(view.webViewClient.shouldInterceptRequest(view, request(url)))
            assertTrue(routes.isEmpty())
            val protected = request("https://meduza.io/story")
            assertFalse(view.webViewClient.shouldOverrideUrlLoading(view, protected))
            assertFalse(view.settings.javaScriptEnabled)
            assertNotNull(view.webViewClient.shouldInterceptRequest(view, protected))
            assertTrue(routes.last().forceProxy)
            assertNotNull(view.webViewClient.shouldInterceptRequest(view, request("https://cdn.example/script.js", false)))
            assertTrue(routes.last().forceProxy)
        }
    }
    @Test fun originalModeForZerkaloStillUsesProxyAndDoesNotFallBackAfterFailure() {
        var calls = 0
        compose.setContent { MaterialTheme { EmbeddedBrowserPage("https://zerkalo-6oct.site/news/story", NewsRouting.DEFAULT_WORKER,
            download = { _, route, _ -> calls++; assertTrue(route.forceProxy); error("Worker unavailable") }) } }
        compose.onNodeWithText("Оригинал").performClick()
        compose.runOnIdle {
            val view = browser()!!
            assertFalse(view.settings.javaScriptEnabled)
            val response = view.webViewClient.shouldInterceptRequest(view, request("https://zerkalo-6oct.site/news/story"))
            assertNotNull(response); assertEquals(502, response!!.statusCode); assertEquals(1, calls)
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request("intent://external")))
        }
    }
    @Test fun originalPolicyRejectsUnsafeLinksProtectedDocumentsAndLockedSessions() {
        assertTrue(BrowserRouting.canLoadOriginal("https://www.the-warsaw.pl/story", "https://www.the-warsaw.pl/story"))
        for (url in listOf("http://example.org", "file:///private/file", "https://user:pass@example.org", "https://meduza.io/story", "https://news.zerkalo.io/story")) {
            assertFalse(BrowserRouting.canLoadOriginal(url, "https://example.org"))
        }
        assertFalse(BrowserRouting.canLoadOriginal("https://cdn.example/asset", "https://meduza.io/story"))
        assertFalse(BrowserRouting.canLoadOriginal("https://example.org", "https://example.org", proxyLocked = true))
    }
}
