package io.newsbrief.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import io.newsbrief.app.network.NewsHttp
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.security.MessageDigest
import io.newsbrief.app.network.NewsRouting
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NewsImage(imageUrl: String, articleUrl: String, workerBase: String, height: Int = 180) {
    if (!imageUrl.startsWith("https://")) return
    val resource = runCatching {
        if (imageUrl.startsWith(workerBase.trimEnd('/') + "/image?"))
            imageUrl.toHttpUrl().queryParameter("url") ?: imageUrl
        else imageUrl
    }.getOrNull() ?: return
    val path = runCatching { NewsRouting.endpoint(resource, "image", workerBase, forceProxy = true) }
        .getOrNull() ?: return
    val cacheDir = File(LocalContext.current.filesDir, "reader-images")
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                cacheDir.mkdirs()
                val key = MessageDigest.getInstance("SHA-256").digest(resource.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val file = File(cacheDir, key)
                val bytes = if (file.isFile) file.readBytes() else
                    NewsHttp.download(resource, "image", workerBase, 8_000_000, forceProxy = true)

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val factor = maxOf(options.outWidth / 1600, options.outHeight / 1600, 1)
                val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = factor })
                if (result != null && !file.isFile) {
                    val temp = File.createTempFile("image", ".tmp", cacheDir)
                    try { temp.writeBytes(bytes); temp.renameTo(file) } finally { temp.delete() }
                }
                result
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = "Иллюстрация новости",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(12.dp)))
    }
}
