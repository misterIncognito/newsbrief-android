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
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NewsImage(imageUrl: String, articleUrl: String, workerBase: String, height: Int = 180, forceProxy: Boolean = false) {
    if (!imageUrl.startsWith("https://")) return
    val resource = runCatching { io.newsbrief.app.network.ArticleImageCache.originalUrl(imageUrl, workerBase) }
        .getOrNull() ?: return
    val filesDir = LocalContext.current.filesDir
    val cache = androidx.compose.runtime.remember(filesDir, articleUrl, forceProxy) {
        io.newsbrief.app.network.ArticleImageCache(File(filesDir, "reader-images"), requireProxy = forceProxy || io.newsbrief.app.network.NewsRouting.requiresProxy(articleUrl))
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = resource, key2 = workerBase, key3 = forceProxy || io.newsbrief.app.network.NewsRouting.requiresProxy(articleUrl)) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = cache.load(resource, workerBase)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val factor = maxOf(options.outWidth / 1600, options.outHeight / 1600, 1)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = factor })
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = "Иллюстрация новости",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(12.dp)))
    }
}
