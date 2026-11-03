package io.newsbrief.app.ui

import androidx.compose.ui.res.stringResource
import io.newsbrief.modules.core.api.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.newsbrief.app.data.ArticleEntity
import io.newsbrief.app.network.ArticleLink
import io.newsbrief.app.network.ArticleLinks
import io.newsbrief.app.network.ArticleReader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun annotatedPostText(text: String, links: List<ArticleLink>, color: Color,
    onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    append(text)
    var cursor = 0
    links.filter { ArticleLinks.valid(it.url) }.forEach { link ->
        val start = text.indexOf(link.label, cursor)
        if (start >= 0 && link.label.isNotBlank()) {
            val end = start + link.label.length
            addLink(LinkAnnotation.Clickable(link.url,
                TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) {
                onLink(link.url)
            }, start, end)
            cursor = end
        }
    }
}

/** Telegram posts are complete feed items; only embedded links navigate away. */
@Composable
internal fun TelegramFeedPost(article: ArticleEntity, workerUrl: String,
    onLink: (String) -> Unit, onBrowser: (String) -> Unit, actions: @Composable () -> Unit = {},
    host: io.newsbrief.app.runtime.FeatureHost? = null) {
    val links = remember(article.linksJson) { ArticleLinks.decode(article.linksJson) }
    val text = article.content.ifBlank { article.excerpt }.ifBlank { article.title }
    val primary = ArticleLinks.fullArticle(links)
    val linkedText = annotatedPostText(text, links, MaterialTheme.colorScheme.primary, onLink)
    val linkedUrls = linkedText.getLinkAnnotations(0, linkedText.length).mapNotNull {
        (it.item as? LinkAnnotation.Clickable)?.tag
    }.toSet()
    val photos = remember(article.imagesJson, article.imageUrl) {
        ArticleReader.decodeImages(article.imagesJson).ifEmpty { listOf(article.imageUrl).filter { it.isNotBlank() } }
    }
    val date = remember(article.publishedAt) {
        DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(article.publishedAt))
    }
    val color = LocalArticleCardColor.current
    Card(Modifier.fillMaxWidth().testTag("telegram-post"),
        colors = if (color != null) CardDefaults.cardColors(containerColor = color, contentColor = Color(0xFF191919)) else CardDefaults.cardColors()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${article.sourceName} · $date", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            photos.forEach { NewsImage(it, article.link, workerUrl, 200, article.proxyRequired, host) }
            SelectionContainer {
                Text(linkedText,
                    modifier = Modifier.testTag("telegram-post-body"), style = MaterialTheme.typography.bodyLarge)
            }
            // Keep every recovered link usable even when an old saved body has different labels.
            links.filter { it.url !in linkedUrls }.forEach { link ->
                TextButton(onClick = { onLink(link.url) }) { Text(link.label) }
            }
            if (primary != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onLink(primary.url) }) { Text(stringResource(R.string.nb_text_021)) }
                TextButton(onClick = { onBrowser(primary.url) }) { Text(stringResource(R.string.nb_text_022)) }
            }
            actions()
        }
    }
}
