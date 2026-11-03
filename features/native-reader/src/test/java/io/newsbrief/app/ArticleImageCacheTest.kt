package io.newsbrief.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.network.ArticleImageCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArticleImageCacheTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Test fun protectedArticleUsesInjectedProxyTransport() = runBlocking {
        val directory = File(app.cacheDir, "protected-injected-image").apply { deleteRecursively() }
        try {
            val image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jG6kAAAAASUVORK5CYII=")
            var required = false
            val cache = ArticleImageCache(directory, proxyDownload = { _, _, force -> required = force; image })
            val article = io.newsbrief.app.data.ArticleEntity(sourceId = 1, sourceName = "s", title = "t", excerpt = "",
                link = "https://example.org/story", publishedAt = 1, proxyRequired = true)
            assertArrayEquals(image, cache.loadForArticle("https://cdn.example/image.png", "https://worker.example", article))
            assertTrue(required)
        } finally { directory.deleteRecursively() }
    }
    @Test fun prefetchedImageIsReadOfflineByAnotherCacheInstance() = runBlocking {
        val directory = File(app.cacheDir, "prefetch-image-test").apply { deleteRecursively() }
        try {
            val image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jG6kAAAAASUVORK5CYII=")
            val url = "https://cdn.example/image.png"
            var calls = 0
            val cache = ArticleImageCache(directory) { _, _ -> calls++; image }
            assertArrayEquals(image, cache.load(url, "https://worker.example"))
            assertTrue(cache.contains(url))
            val offline = ArticleImageCache(directory) { _, _ -> error("Offline") }
            assertArrayEquals(image, offline.load(url, "https://worker.example"))
            assertEquals(1, calls)
        } finally { directory.deleteRecursively() }
    }
    @Test fun htmlErrorResponsesAreNeverSavedAsOfflineImages() = runBlocking {
        val directory = File(app.cacheDir, "prefetch-invalid-image").apply { deleteRecursively() }
        try {
            val url = "https://cdn.example/image.png"
            val cache = ArticleImageCache(directory) { _, _ -> "<html>Denied</html>".toByteArray() }
            assertTrue(runCatching { cache.load(url, "https://worker.example") }.isFailure)
            assertFalse(cache.contains(url))
        } finally { directory.deleteRecursively() }
    }
}
