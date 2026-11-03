package io.newsbrief.app.network

import io.newsbrief.modules.features.telegram_source.R
import io.newsbrief.app.data.*
import io.newsbrief.app.util.FeedRules

class TelegramPreviewSourcePlugin(private val fetch: (String) -> String) : NewsSourcePlugin {
    override val info = SourcePluginInfo(SourceKind.TELEGRAM, "Telegram", "@channel or https://t.me/channel", addressHintResource = R.string.nb_telegram_address_hint)
    override fun canonicalAddress(input: String) = FeedRules.canonicalAddress(input, info.id)
    override suspend fun collect(source: SourceEntity) = TelegramPreviewParser().parse(fetch(source.address), source)
}
