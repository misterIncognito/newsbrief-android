package io.newsbrief.app.network

data class DownloadedResource(val bytes: ByteArray, val mimeType: String, val encoding: String?, val finalUrl: String)

interface HttpTransport {
    fun resource(request: PageRequest, configuration: String, maxBytes: Int): DownloadedResource
}
