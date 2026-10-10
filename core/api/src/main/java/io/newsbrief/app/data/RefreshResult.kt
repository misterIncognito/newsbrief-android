package io.newsbrief.app.data

data class RefreshResult(val added: Int, val sources: Int, val errors: List<String>,
    val skipped: Boolean = false, val retryAfterMillis: Long = 0)
