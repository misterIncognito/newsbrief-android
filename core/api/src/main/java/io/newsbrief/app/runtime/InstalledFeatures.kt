package io.newsbrief.app.runtime

import java.util.ServiceLoader

/** Each installed library contributes its own service descriptor; the host knows only this API. */
object InstalledFeatures {
    fun create(): List<AppFeature> = ServiceLoader.load(AppFeature::class.java, AppFeature::class.java.classLoader)
        .toList().also { features ->
            require(features.all { it.id.isNotBlank() }) { "Blank feature ID" }
            require(features.map { it.id }.distinct().size == features.size) { "Duplicate feature ID" }
        }.sortedBy { it.order }
}
