package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import io.newsbrief.app.data.NewsDatabase
import io.newsbrief.app.data.NewsRepository
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.worker.RefreshWorker

class NewsBriefApp : Application(), io.newsbrief.app.sync.BackgroundTasksOwner {
    override val backgroundTasks = object : io.newsbrief.app.sync.BackgroundTasks {
        override suspend fun refresh() = repository.refresh()
        override suspend fun prefetch() = prefetcher.prefetch()
    }
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: NewsRepository
        private set

    lateinit var prefetcher: io.newsbrief.app.network.ArticlePrefetcher
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val database = Room.databaseBuilder(this, NewsDatabase::class.java, "newsbrief.sqlite")
            .addMigrations(io.newsbrief.app.data.NewsMigrations.FROM_1_2, io.newsbrief.app.data.NewsMigrations.FROM_2_3, io.newsbrief.app.data.NewsMigrations.FROM_3_4, io.newsbrief.app.data.NewsMigrations.FROM_4_5, io.newsbrief.app.data.NewsMigrations.FROM_5_6, io.newsbrief.app.data.NewsMigrations.FROM_6_7, io.newsbrief.app.data.NewsMigrations.FROM_7_8).build()
        val telegram = io.newsbrief.app.network.TelegramProxyClient {
            io.newsbrief.app.network.TelegramProxyConfig(settings.telegramProxyUrl(), settings.telegramProxyKey())
        }
        val sources = io.newsbrief.app.network.SourcePluginRegistry.builtIns(settings, telegram)
        val articles = io.newsbrief.app.network.ArticleFetchRegistry.builtIns(telegram)
        repository = NewsRepository(database, sources, settings, sourcePlugins = sources,
            articleReader = io.newsbrief.app.network.ArticleReader(settings, plugins = articles, telegram = io.newsbrief.app.network.TelegramArticleContent(settings, telegram)),
            onFeedUpdated = { io.newsbrief.app.worker.PrefetchWorker.enqueue(this) })
        prefetcher = io.newsbrief.app.network.ArticlePrefetcher(repository, settings,
            io.newsbrief.app.network.ArticleImageCache(java.io.File(filesDir, "reader-images")))
        RefreshWorker.schedule(this)
    }
}
