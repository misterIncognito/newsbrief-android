package io.newsbrief.app.network

import java.net.URI

/** Transport URL validation shared by credentials, sources and the image cache. */
object ProxyUrls {
    fun validResource(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun telegramService(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Нужен HTTPS-адрес Telegram-прокси без пароля и параметров"
        }
        return uri
    }
}
