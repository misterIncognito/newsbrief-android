package io.newsbrief.app.network

import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared by background prefetch and the visible reader, using the existing cache location. */
class ArticleImageCache(private val directory: File, private val requireProxy: Boolean = false,
    private val proxyDownload: ((String, String, Boolean) -> ByteArray)? = null,
    private val download: (String, String) -> ByteArray = { _, _ -> error("Networking plugin is unavailable") }
) : PrefetchImageStore {
    private fun file(url: String): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })
    override fun contains(url: String): Boolean = file(url).isFile
    override suspend fun load(url: String, worker: String): ByteArray = withContext(Dispatchers.IO) {
        require(ProxyUrls.validResource(url)) { "Invalid image URL" }
        val file = file(url)
        if (file.isFile) return@withContext file.readBytes()
        val bytes = proxyDownload?.invoke(url, worker, requireProxy) ?: download(url, worker)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image response" }
        directory.mkdirs()
        val temp = File.createTempFile("image", ".tmp", directory)
        try {
            temp.writeBytes(bytes)
            check(temp.renameTo(file) || file.isFile) { "Image cache write failed" }
        } finally { temp.delete() }
        bytes
    }

    override suspend fun loadForArticle(url: String, worker: String, article: io.newsbrief.app.data.ArticleEntity): ByteArray {
        if (!article.proxyRequired && !NewsRouting.requiresProxy(article.link)) return load(url, worker)
        return ArticleImageCache(directory, requireProxy = true, proxyDownload = proxyDownload, download = download).load(url, worker)
    }
}
