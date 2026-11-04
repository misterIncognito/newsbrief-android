package io.newsbrief.app

import io.newsbrief.app.data.SourceKind
import io.newsbrief.app.network.DirectoryParser
import io.newsbrief.app.network.DirectorySource
import io.newsbrief.app.ui.sortTelegramResults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelegramCatalogTest {
    @Test fun catalogAndCachedResultsPreserveVerifiedStatusAndUnknowns() {
        val parsed = DirectoryParser.tgden("""{"total":4,"items":[
            {"username":"channelone","subscribers_count":100,"verified":true},
            {"username":"channeltwo","subscribers_count":0,"verified":false},
            {"username":"channelthree"},
            {"username":"channelfour","verified":"true"}
        ]}""")
        assertEquals(listOf(true, false, null, null), parsed.items.map { it.verified })
        assertEquals(listOf(100L, 0L, null, null), parsed.items.map { it.audience })
        assertEquals(parsed.items, DirectoryParser.decode(DirectoryParser.encode(parsed)).items)
        val oldCache = JSONObject(DirectoryParser.encode(parsed))
        oldCache.getJSONArray("items").getJSONObject(0).remove("verified")
        assertNull(DirectoryParser.decode(oldCache.toString()).items.first().verified)
    }

    @Test fun sortingAndVerificationUseOnlyKnownValuesAndPreserveTies() {
        fun channel(name: String, count: Long?, verified: Boolean?) =
            DirectorySource(name, "https://t.me/$name", SourceKind.TELEGRAM, audience = count, verified = verified)
        val unknown = channel("unknown", null, null)
        val zero = channel("zero", 0, true)
        val first = channel("first", 10, true)
        val second = channel("second", 10, false)
        val items = listOf(unknown, zero, first, second)
        assertEquals(items, sortTelegramResults(items, false, false))
        assertEquals(listOf(first, second, zero, unknown), sortTelegramResults(items, true, false))
        assertEquals(listOf(zero, first), sortTelegramResults(items, false, true))
        assertEquals(listOf(first, zero), sortTelegramResults(items, true, true))
    }
}
