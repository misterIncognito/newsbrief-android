package io.newsbrief.app.network

import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

interface PrefetchImageStore {
    fun contains(url: String): Boolean
    suspend fun load(url: String, worker: String): ByteArray
}

/** Shared by background prefetch and the visible reader, using the existing cache location. */
class ArticleImageCache(private val directory: File,
    private val download: (String, String) -> ByteArray = { url, worker ->
        NewsHttp.download(url, "image", worker, 8_000_000, forceProxy = true)
    }
) : PrefetchImageStore {
    private fun file(url: String): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })
    override fun contains(url: String): Boolean = file(url).isFile
    override suspend fun load(url: String, worker: String): ByteArray = withContext(Dispatchers.IO) {
        require(ArticleLinks.valid(url)) { "Invalid image URL" }
        val file = file(url)
        if (file.isFile) return@withContext file.readBytes()
        val bytes = download(url, worker)
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

    companion object {
        fun originalUrl(url: String, worker: String): String =
            if (url.startsWith(worker.trimEnd('/') + "/image?")) url.toHttpUrl().queryParameter("url") ?: url else url
    }
}
