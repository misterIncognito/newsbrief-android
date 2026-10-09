package io.newsbrief.app

import io.newsbrief.app.util.FeedRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FeedRulesTest {
    @Test fun telegramHandleIsNormalized() {
        assertEquals("https://t.me/s/BILD_Russian", FeedRules.canonicalAddress("@BILD_Russian", "TELEGRAM"))
        assertEquals("https://t.me/s/bbcrussian", FeedRules.canonicalAddress("https://t.me/bbcrussian", "TELEGRAM"))
    }

    @Test fun maliciousTelegramHostIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedRules.canonicalAddress("https://t.me.evil.com/fake", "TELEGRAM")
        }
    }

    @Test fun rssMustUseHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedRules.canonicalAddress("http://news.example.org/feed.xml", "RSS")
        }
    }

    @Test fun canonicalTitleRemovesNoise() {
        assertEquals("главное за день", FeedRules.normalizedTitle("Главное: за день!"))
    }
}
