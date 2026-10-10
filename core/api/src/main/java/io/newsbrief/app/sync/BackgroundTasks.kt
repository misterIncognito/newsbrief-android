package io.newsbrief.app.sync

interface BackgroundTasks {
    suspend fun refresh(): io.newsbrief.app.data.RefreshResult
    suspend fun prefetch(): PrefetchOutcome
}
interface BackgroundTasksOwner { val backgroundTasks: BackgroundTasks }

data class PrefetchOutcome(val articles: Int, val images: Int, val failures: Int)
