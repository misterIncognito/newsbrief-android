package io.newsbrief.app.util

import io.newsbrief.modules.core.api.R
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
                requireLocalized(uri.scheme.equals("https", ignoreCase = true), R.string.nb_err_telegram_https, "Нужна HTTPS-ссылка на канал")
                requireLocalized(uri.host?.lowercase(Locale.ROOT) in setOf("t.me", "telegram.me", "www.t.me"), R.string.nb_err_telegram_public, "Поддерживаются только публичные Telegram-каналы t.me")
                val segments = uri.path.trim('/').split('/')
                if (segments.firstOrNull() == "s") segments.getOrNull(1) ?: "" else segments.firstOrNull() ?: ""
            }
            requireLocalized(channel.matches(handle), R.string.nb_err_telegram_handle, "Неверное имя Telegram-канала")
            return "https://t.me/s/$handle"
        }
        requireLocalized(kind == "RSS", R.string.nb_err_source_kind, "Неизвестный тип источника")
        val uri = URI(raw)
        requireLocalized(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank(), R.string.nb_err_rss_https, "Нужна полная HTTPS-ссылка на RSS")
        requireLocalized(uri.userInfo == null, R.string.nb_err_url_password, "Ссылка не должна содержать пароль")
        return uri.toString()
    }

    fun headline(text: String): String = text
        .trim().lineSequence().firstOrNull().orEmpty()
        .trim().take(180).ifBlank { "Публикация" }

    fun normalizedTitle(text: String): String = text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")
}
