package io.newsbrief.app.network

/** Provider-neutral request. Policy remains required across redirects and extraction. */
data class PageRequest(val resource: String, val type: String, val proxyRequired: Boolean, val preferProxy: Boolean = false) {
    val requiresProxy get() = proxyRequired || NewsRouting.requiresProxy(resource)
}
interface PageProxyPlugin {
    val id: String
    fun supports(request: PageRequest): Boolean
    fun endpoint(request: PageRequest, configuration: String): String
    fun originalResource(url: String, configuration: String): String? = null
}
interface PageRouter {
    fun route(request: PageRequest, configuration: String): String
}
object DirectPageRouter : PageRouter {
    override fun route(request: PageRequest, configuration: String): String {
        require(ProxyUrls.validResource(request.resource)) { "Only HTTPS resource URLs are allowed" }
        require(request.type in setOf("fetch", "article", "image")) { "Unsupported endpoint" }
        check(!request.requiresProxy) { "Required page proxy plugin is unavailable" }
        return request.resource
    }
}
