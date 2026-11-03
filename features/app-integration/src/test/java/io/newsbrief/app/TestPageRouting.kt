package io.newsbrief.app

import io.newsbrief.app.network.CloudflarePageProxy
import io.newsbrief.app.network.PageProxyRegistry
import io.newsbrief.app.network.PageRequest

internal fun routeWithNewsWorker(url: String, type: String, worker: String, forceProxy: Boolean = false): String =
    PageProxyRegistry(listOf(CloudflarePageProxy())).route(PageRequest(url, type, forceProxy), worker)
