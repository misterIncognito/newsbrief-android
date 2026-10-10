package io.newsbrief.app.network

/** The first matching plugin is terminal. Failure never selects a direct fallback. */
class PageProxyRegistry(private val plugins: List<PageProxyPlugin>) : PageRouter {
    init { require(plugins.map { it.id }.distinct().size == plugins.size) { "Duplicate page proxy plugin ID" } }
    override fun route(request: PageRequest, configuration: String): String {
        require(ProxyUrls.validResource(request.resource)) { "Only HTTPS resource URLs are allowed" }
        require(request.type in setOf("fetch", "article", "image")) { "Unsupported endpoint" }
        if (!request.requiresProxy && !request.preferProxy) return request.resource
        val plugin = plugins.firstOrNull { it.supports(request) }
            ?: return DirectPageRouter.route(request, configuration)
        val endpoint = plugin.endpoint(request, configuration)
        require(ProxyUrls.validResource(endpoint) && endpoint != request.resource) { "Invalid proxy endpoint" }
        return endpoint
    }
}
