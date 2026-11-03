package io.newsbrief.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.newsbrief.app.data.ArticleEntity

internal fun articleShareIntent(article: ArticleEntity): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TITLE, article.title)
    putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
}


class ShareArticleAction : ArticleAction {
    @Composable override fun Content(article: ArticleEntity, host: io.newsbrief.app.runtime.FeatureHost) { ShareArticleButton(article) }
}
@Composable internal fun ShareArticleButton(article: ArticleEntity) {
        val context = LocalContext.current
        TextButton(onClick = {
            try { context.startActivity(Intent.createChooser(articleShareIntent(article), "Поделиться")) }
            catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "Нет доступного приложения для отправки", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Поделиться") }
    }
