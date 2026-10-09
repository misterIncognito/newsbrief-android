package io.newsbrief.app.sync

interface BackgroundTasks {
    suspend fun refresh(): FeedSynchronizer.RefreshResult
    suspend fun prefetch(): io.newsbrief.app.network.ArticlePrefetcher.Result
}
interface BackgroundTasksOwner { val backgroundTasks: BackgroundTasks }
