package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import io.newsbrief.app.data.NewsDatabase
import io.newsbrief.app.data.NewsRepository
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.network.WebCollectors
import io.newsbrief.app.worker.RefreshWorker

class NewsBriefApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: NewsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val database = Room.databaseBuilder(this, NewsDatabase::class.java, "newsbrief.sqlite")
            .addMigrations(io.newsbrief.app.data.NewsMigrations.FROM_1_2, io.newsbrief.app.data.NewsMigrations.FROM_2_3, io.newsbrief.app.data.NewsMigrations.FROM_3_4, io.newsbrief.app.data.NewsMigrations.FROM_4_5, io.newsbrief.app.data.NewsMigrations.FROM_5_6).build()
        repository = NewsRepository(database, WebCollectors(settings), settings)
        RefreshWorker.schedule(this)
    }
}
