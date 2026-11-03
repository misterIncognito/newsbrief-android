package io.newsbrief.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import io.newsbrief.app.sync.BackgroundTasksOwner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tasks = (applicationContext as? BackgroundTasksOwner)?.backgroundTasks ?: return Result.failure()
        return try {
            val response = tasks.refresh()
            if (response.errors.isNotEmpty() && response.errors.size == response.sources) Result.retry()
            else Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "refresh-news", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
