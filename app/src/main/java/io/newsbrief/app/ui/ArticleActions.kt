package io.newsbrief.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity

internal fun articleShareIntent(article: ArticleEntity): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TITLE, article.title)
    putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
}

@Composable
internal fun ArticleActions(article: ArticleEntity, saved: Boolean, onSave: () -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onSave) { Text(if (saved) "✓ Сохранено" else "Сохранить") }
        TextButton(onClick = {
            try { context.startActivity(Intent.createChooser(articleShareIntent(article), "Поделиться")) }
            catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "Нет доступного приложения для отправки", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Поделиться") }
    }
}
