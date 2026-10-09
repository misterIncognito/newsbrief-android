package io.newsbrief.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opt in with -PverifyAmpReaders=true; these publisher URLs may change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmpReaderLiveTest {
    private fun verify(url: String, expectedAmp: String?) = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val store = SettingsStore(ApplicationProvider.getApplicationContext<Application>())
        val reader = ArticleReader(store, proxyDownload = { target, type, _, limit ->
            requests += target to type
            NewsHttp.download(target, type, NewsRouting.DEFAULT_WORKER, limit, forceProxy = true)
        }, download = { _, _, _, _ -> error("Publisher request escaped proxy") })
        val result = reader.load(ArticleEntity(sourceId = 1, sourceName = "Live validation", title = "Preview",
            excerpt = "Summary", link = url, publishedAt = 1, proxyRequired = true))
        assertNull(result.notice)
        assertTrue(result.text.length > 500)
        assertTrue(result.title != "Preview" && result.title.isNotBlank())
        assertTrue(result.images.isNotEmpty())
        assertNotNull(result.publishedAt)
        assertEquals(url, result.originalUrl)
        if (expectedAmp != null) {
            assertEquals(listOf(url to "fetch", expectedAmp to "fetch"), requests)
        } else {
            assertEquals(listOf(url to "fetch", url to "article"), requests)
        }
        println("Live reader: $url; AMP=${expectedAmp != null}; characters=${result.text.length}; images=${result.images.size}; requests=${requests.size}")
        assertNotEquals("https://meduza.io/images/share-meduza.png", result.imageUrl)
        verifyImage(result.imageUrl)
    }
    private suspend fun verifyImage(url: String) {
        val directory = java.nio.file.Files.createTempDirectory("newsbrief-amp-live-").toFile()
        try {
            val bytes = ArticleImageCache(directory).load(url, NewsRouting.DEFAULT_WORKER)
            val offline = ArticleImageCache(directory) { _, _ -> error("Offline image used network") }
            assertArrayEquals(bytes, offline.load(url, NewsRouting.DEFAULT_WORKER))
            println("Live cover: $url; coverBytes=${bytes.size}; offlineCache=true")
        } finally { directory.deleteRecursively() }
    }
    @Test fun meduzaAmpThroughWorker() = verify(
        "https://meduza.io/news/2026/10/08/segodnya-rech-idet-o-vyzhivanii-klichko-posovetoval-zhitelyam-kieva-vremenno-uehat-iz-goroda",
        "https://meduza.io/amp/news/2026/10/08/segodnya-rech-idet-o-vyzhivanii-klichko-posovetoval-zhitelyam-kieva-vremenno-uehat-iz-goroda")
    @Test fun zerkaloMirrorAmpThroughWorker() = verify(
        "https://zerkalo-6oct.site/news/economics/138172.html?tg=6",
        "https://zerkalo-6oct.site/news/amp/economics/138172.html")
    @Test fun devbyWithoutAmpUsesWorkerExtraction() = verify(
        "https://devby.io/news/ii-boty-podsovyvaut-polzovatelyam-tovary-podorozhe", null)
}
