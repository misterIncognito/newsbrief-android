package io.newsbrief.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NewsMigrations {
    val FROM_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN content TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE articles ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
        }
    }
    val FROM_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN readerLoaded INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE articles ADD COLUMN author TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE articles ADD COLUMN imagesJson TEXT NOT NULL DEFAULT '[]'")
        }
    }
}
