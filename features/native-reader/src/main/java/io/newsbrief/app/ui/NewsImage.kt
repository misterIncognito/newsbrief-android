package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NewsImage(imageUrl: String, articleUrl: String, workerBase: String, height: Int = 180, forceProxy: Boolean = false,
    host: io.newsbrief.app.runtime.FeatureHost? = null) {
    if (!imageUrl.startsWith("https://")) return
    val bindings = host?.bindings ?: return
    val resource = runCatching {
        bindings.pageProxyPlugins.firstNotNullOfOrNull { it.originalResource(imageUrl, workerBase) } ?: imageUrl
    }.getOrNull() ?: return
    val context = LocalContext.current
    val cache = androidx.compose.runtime.remember(host, context) { bindings.images?.invoke(context) } ?: return
    val article = io.newsbrief.app.data.ArticleEntity(sourceId = 0, sourceName = "", title = "", excerpt = "",
        link = articleUrl, publishedAt = 0, proxyRequired = forceProxy)
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = resource, key2 = workerBase, key3 = forceProxy || io.newsbrief.app.network.NewsRouting.requiresProxy(articleUrl)) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = cache.loadForArticle(resource, workerBase, article)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val factor = maxOf(options.outWidth / 1600, options.outHeight / 1600, 1)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = factor })
            }.getOrNull()
        }
    }
    NewsImageSlot(height) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = stringResource(R.string.nb_text_020),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(height.dp))
        }
    }
}

/** Keep the same footprint while the image loads or if it cannot be downloaded. */
@Composable
internal fun NewsImageSlot(height: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)) { content() }
}
