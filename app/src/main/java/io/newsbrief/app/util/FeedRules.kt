package io.newsbrief.app.util

import java.net.URI
import java.util.Locale

/** Small dependency-free helpers, shared by ingestion and JVM tests. */
object FeedRules {
    private val channel = Regex("^[A-Za-z][A-Za-z0-9_]{4,31}$")

    fun canonicalAddress(input: String, kind: String): String {
        val raw = input.trim()
        if (kind == "TELEGRAM") {
            val handle = if (raw.startsWith("@")) raw.drop(1) else {
                val uri = URI(raw)
                require(uri.scheme.equals("https", ignoreCase = true)) { "Нужна HTTPS-ссылка на канал" }
                require(uri.host?.lowercase(Locale.ROOT) in setOf("t.me", "telegram.me", "www.t.me")) {
                    "Поддерживаются только публичные Telegram-каналы t.me"
                }
                val segments = uri.path.trim('/').split('/')
                if (segments.firstOrNull() == "s") segments.getOrNull(1) ?: "" else segments.firstOrNull() ?: ""
            }
            require(channel.matches(handle)) { "Неверное имя Telegram-канала" }
            return "https://t.me/s/$handle"
        }
        require(kind == "RSS") { "Неизвестный тип источника" }
        val uri = URI(raw)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Нужна полная HTTPS-ссылка на RSS"
        }
        require(uri.userInfo == null) { "Ссылка не должна содержать пароль" }
        return uri.toString()
    }

    fun headline(text: String): String = text
        .trim().lineSequence().firstOrNull().orEmpty()
        .trim().take(180).ifBlank { "Публикация" }

    fun normalizedTitle(text: String): String = text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")
}
