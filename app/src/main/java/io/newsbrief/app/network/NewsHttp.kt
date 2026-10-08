package io.newsbrief.app.network

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Every redirect is routed again; required-proxy requests never escape the Worker. */
object NewsHttp {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()

    internal fun download(resource: String, type: String, worker: String, maxBytes: Int,
                 forceProxy: Boolean = false, transport: OkHttpClient = client): ByteArray {
        var target = resource
        var mustProxy = forceProxy || NewsRouting.requiresProxy(resource)
        repeat(4) { redirect ->
            val route = NewsRouting.endpoint(target, type, worker, mustProxy)
            transport.newCall(Request.Builder().url(route).header("User-Agent", "NewsBrief/0.4")
                .header("Accept", "application/json, application/rss+xml, application/atom+xml, text/html, image/*").build())
                .execute().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        check(!mustProxy) { "Unexpected proxy redirect" }
                        check(redirect < 3) { "Too many redirects" }
                        target = response.request.url.resolve(response.header("Location") ?: error("Missing redirect"))
                            ?.toString() ?: error("Invalid redirect")
                        mustProxy = NewsRouting.requiresProxy(target)
                    } else {
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = response.body ?: error("Empty response")
                        require(body.contentLength() <= maxBytes) { "Response too large" }
                        return body.byteStream().use { stream ->
                            val out = ByteArrayOutputStream()
                            val bytes = ByteArray(8192)
                            var count = stream.read(bytes)
                            while (count != -1) {
                                require(out.size() + count <= maxBytes) { "Response too large" }
                                out.write(bytes, 0, count)
                                count = stream.read(bytes)
                            }
                            out.toByteArray()
                        }
                    }
                }
        }
        error("Too many redirects")
    }
}
