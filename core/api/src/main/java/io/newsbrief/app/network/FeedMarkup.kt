package io.newsbrief.app.network

import org.jsoup.nodes.Element

/** Feed and reader text conversion must preserve the same paragraph boundaries. */
object FeedMarkup {
    fun htmlText(element: Element): String {
            element.select("script,style,nav,footer,aside,form,template,amp-ad,amp-sticky-ad,amp-analytics,amp-auto-ads,amp-sidebar").remove()
            element.select("p,div,h1,h2,h3,h4,li,blockquote,br").forEach { it.appendText("\n\n") }
            return element.wholeText().replace(Regex("[\\t ]+"), " ")
                .replace(Regex(" *\n *"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }
}
