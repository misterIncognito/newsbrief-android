package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ArticleLinks

internal fun articleShareIntent(article: ArticleEntity): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    if (ArticleLinks.isTelegramPost(article)) {
        putExtra(Intent.EXTRA_TEXT, article.link)
    } else {
        putExtra(Intent.EXTRA_TITLE, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
}


class ShareArticleAction : ArticleAction {
    @Composable override fun Content(article: ArticleEntity, host: io.newsbrief.app.runtime.FeatureHost) { ShareArticleButton(article) }
}
@Composable internal fun ShareArticleButton(article: ArticleEntity) {
        val context = LocalContext.current
        TextButton(onClick = {
            try { context.startActivity(Intent.createChooser(articleShareIntent(article), context.getString(R.string.nb_share))) }
            catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.nb_share_unavailable), Toast.LENGTH_SHORT).show()
            }
        }) { Text(context.getString(R.string.nb_share)) }
    }
