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
import io.newsbrief.app.network.ArticleReader
import io.newsbrief.app.network.NewsRouting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NewsImage(imageUrl: String, articleUrl: String, workerBase: String, height: Int = 180) {
    if (!imageUrl.startsWith("https://")) return
    val path = runCatching {
        if (imageUrl.startsWith(workerBase.trimEnd('/') + "/image?")) imageUrl
        else NewsRouting.endpoint(imageUrl, "image", workerBase,
            forceProxy = NewsRouting.requiresProxy(articleUrl))
    }.getOrNull() ?: return
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ArticleReader.download(path, 4_000_000)
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
