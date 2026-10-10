package io.newsbrief.app.network

import io.newsbrief.app.runtime.FeatureBindings

data class DownloadedResource(val bytes: ByteArray, val mimeType: String, val encoding: String?, val finalUrl: String)

interface HttpTransport {
    fun resource(request: PageRequest, configuration: String, maxBytes: Int): DownloadedResource
}
object HttpRequests {
    fun download(resource: String, type: String, worker: String, maxBytes: Int,
        forceProxy: Boolean = false, preferProxy: Boolean = false): ByteArray =
        downloadResource(resource, type, worker, maxBytes, forceProxy, preferProxy).bytes
    fun downloadResource(resource: String, type: String, worker: String, maxBytes: Int,
        forceProxy: Boolean = false, preferProxy: Boolean = false): DownloadedResource =
        (FeatureBindings.create().http ?: error("Networking plugin is unavailable"))
            .resource(PageRequest(resource, type, forceProxy, preferProxy), worker, maxBytes)
}
object ArticleParsing {
    fun alternative(raw: String, url: String): String? = FeatureBindings.create().ampAlternative(raw, url)
    fun parse(raw: String, url: String): ReaderArticle =
        (FeatureBindings.create().parseArticle ?: error("Article parser plugin is unavailable"))(raw, url)
}
