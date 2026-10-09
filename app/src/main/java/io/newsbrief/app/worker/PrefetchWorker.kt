package io.newsbrief.app.worker

import android.content.Context
import androidx.work.*
import io.newsbrief.app.NewsBriefApp
import kotlinx.coroutines.CancellationException

/** Feed refresh finishes immediately; full articles download in a separate durable job. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NewsBriefApp
        return try {
            val result = app.prefetcher.prefetch()
            Result.success(workDataOf("articles" to result.articles, "images" to result.images, "failures" to result.failures))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PrefetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("prefetch-articles", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
