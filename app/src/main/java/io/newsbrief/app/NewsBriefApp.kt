package io.newsbrief.app

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.newsbrief.app.data.NewsDatabase
import io.newsbrief.app.data.NewsRepository
import io.newsbrief.app.data.SettingsStore
import io.newsbrief.app.network.WebCollectors
import io.newsbrief.app.worker.RefreshWorker

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE articles ADD COLUMN content TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE articles ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
    }
}

class NewsBriefApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: NewsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val database = Room.databaseBuilder(this, NewsDatabase::class.java, "newsbrief.sqlite")
            .addMigrations(MIGRATION_1_2).build()
        repository = NewsRepository(database, WebCollectors(settings), settings)
        RefreshWorker.schedule(this)
    }
}
