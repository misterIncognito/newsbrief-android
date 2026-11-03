package io.newsbrief.app.ui

import io.newsbrief.modules.core.api.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.newsbrief.app.network.ProxyUrls

fun externalArticleIntent(url: String): Intent {
    require(ProxyUrls.validResource(url)) { "Invalid article URL" }
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
fun openExternalArticle(context: Context, url: String) {
    try { context.startActivity(externalArticleIntent(url)) }
    catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.nb_no_browser), Toast.LENGTH_SHORT).show()
    }
}
