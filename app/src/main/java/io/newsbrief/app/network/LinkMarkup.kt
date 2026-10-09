package io.newsbrief.app.network

import org.jsoup.nodes.Element

fun ArticleLinks.extract(element: Element): List<ArticleLink> = element.select("a[href]")
        .map { ArticleLink(it.text().trim().ifBlank { it.absUrl("href") }, it.absUrl("href")) }
        .filter { valid(it.url) }.distinctBy { it.url }.take(50)
