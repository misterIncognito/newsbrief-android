package io.newsbrief.app.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.newsbrief.app.network.ArticleLinks
import io.newsbrief.app.network.BrowserRouting
import io.newsbrief.app.network.NewsHttp
import java.io.ByteArrayInputStream
import java.net.URI

@Composable
internal fun EmbeddedBrowserPage(url: String, worker: String) {
    var browser by remember(url) { mutableStateOf<WebView?>(null) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var location by remember(url) { mutableStateOf(url) }
    BackHandler(enabled = canGoBack) { browser?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(runCatching { URI(location).host }.getOrNull().orEmpty(),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { browser?.reload() }) { Text("Перезагрузить") }
        }
        if (progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
            WebView(context).apply {
                browser = this
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                val documentUrl = java.util.concurrent.atomic.AtomicReference(url)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Keep navigation inside this view and never launch external apps.
                        return !ArticleLinks.valid(request.url.toString()) || request.method != "GET"
                    }
                    override fun onPageStarted(view: WebView, target: String, icon: android.graphics.Bitmap?) {
                        documentUrl.set(target)
                        location = target
                    }
                    override fun doUpdateVisitedHistory(view: WebView, target: String?, reload: Boolean) {
                        canGoBack = view.canGoBack()
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        return try {
                            require(request.method == "GET") { "Поддерживается чтение публичных страниц" }
                            val target = request.url.toString()
                            val route = BrowserRouting.route(target, request.isForMainFrame,
                                documentUrl.get(), request.requestHeaders.entries.firstOrNull {
                                    it.key.equals("Accept", ignoreCase = true)
                                }?.value.orEmpty())
                            val result = NewsHttp.downloadResource(target, route.type, worker, 5_000_000, route.forceProxy)
                            if (request.isForMainFrame) documentUrl.set(result.finalUrl)
                            if (request.isForMainFrame && result.finalUrl != target && result.mimeType == "text/html") {
                                // WebView must resolve relative links against the final document after a routed redirect.
                                val html = org.jsoup.Jsoup.parse(result.bytes.toString(
                                    java.nio.charset.Charset.forName(result.encoding ?: "UTF-8")), result.finalUrl)
                                html.select("base").remove()
                                html.head().prependElement("base").attr("href", result.finalUrl)
                                WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.outerHtml().toByteArray()))
                            } else WebResourceResponse(result.mimeType, result.encoding, ByteArrayInputStream(result.bytes))
                        } catch (e: Exception) {
                            // A concrete error response prevents WebView from trying a direct fallback.
                            val message = org.jsoup.nodes.Entities.escape(e.message.orEmpty().take(120))
                            val body = "<html><meta charset='utf-8'><body><h2>Не удалось загрузить страницу</h2><p>$message</p><p>Вернись к сохранённой статье или повтори загрузку.</p></body></html>"
                            WebResourceResponse("text/html", "UTF-8", 502, "Unavailable", emptyMap(),
                                ByteArrayInputStream(body.toByteArray()))
                        }
                    }
                }
                loadUrl(url)
            }
        })
    }
    DisposableEffect(url) {
        onDispose { browser?.apply { stopLoading(); destroy() }; browser = null }
    }
}
