package io.newsbrief.app.util

import android.content.Context
import androidx.annotation.StringRes

/** Carries a resource identifier across feature boundaries without storing a Context. */
class LocalizedFailure(
    @StringRes val resource: Int,
    fallback: String,
    vararg val arguments: Any
) : IllegalArgumentException(fallback)

fun requireLocalized(condition: Boolean, @StringRes resource: Int, fallback: String, vararg arguments: Any) {
    if (!condition) throw LocalizedFailure(resource, fallback, *arguments)
}

fun Context.failureMessage(error: Throwable): String =
    if (error is LocalizedFailure) getString(error.resource, *error.arguments)
    else error.localizedMessage.orEmpty()
