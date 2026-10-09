package io.newsbrief.app.network

import java.net.URI

/** Transport URL validation shared by credentials, sources and the image cache. */
object ProxyUrls {
    fun validResource(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun workerService(value: String): URI {
        val uri = URI(value.trim().trimEnd('/'))
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null) { "Invalid HTTPS Worker URL" }
        return uri
    }

    fun telegramService(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Нужен HTTPS-адрес Telegram-прокси без пароля и параметров"
        }
        return uri
    }
}
