package io.newsbrief.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NewsMigrations {
    val FROM_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sources ADD COLUMN historyGap INTEGER NOT NULL DEFAULT 0")
        }
    }

    val FROM_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sources ADD COLUMN fetchCursor TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE articles ADD COLUMN readAt INTEGER")
            // Existing installations already have a recent feed. Use the newest saved
            // article as the starting anchor, so the first refresh can catch up from it.
            db.query("SELECT id, kind FROM sources").use { sources ->
                while (sources.moveToNext()) {
                    val sourceId = sources.getLong(0)
                    val kind = sources.getString(1)
                    var rssAnchor: String? = null
                    var telegramId: Long? = null
                    db.query("SELECT link FROM articles WHERE sourceId = ? ORDER BY publishedAt DESC", arrayOf(sourceId)).use { articles ->
                        while (articles.moveToNext()) {
                            val link = articles.getString(0)
                            if (kind == "RSS" && rssAnchor == null && link.startsWith("https://")) rssAnchor = link
                            if (kind == "TELEGRAM" || kind == "TELEGRAM_PROXY") {
                                val uri = runCatching { java.net.URI(link) }.getOrNull() ?: continue
                                val tail = uri.path.orEmpty().substringAfterLast('/').substringAfterLast(':')
                                val id = tail.toLongOrNull()
                                if (id != null && id > (telegramId ?: 0L)) telegramId = id
                            }
                        }
                    }
                    val anchor = if (kind == "RSS") rssAnchor else telegramId?.toString()
                    if (anchor != null) db.execSQL("UPDATE sources SET fetchCursor = ? WHERE id = ?", arrayOf<Any>(anchor, sourceId))
                }
            }
        }
    }

    val FROM_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN sourcePluginId TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE articles SET sourcePluginId = COALESCE((SELECT kind FROM sources WHERE sources.id = articles.sourceId), '')")
        }
    }
    val FROM_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_articles (link TEXT NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(link))")
        }
    }
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
    val FROM_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sources ADD COLUMN categoriesJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("CREATE TABLE IF NOT EXISTS catalog_searches (queryKey TEXT NOT NULL, responseJson TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(queryKey))")
        }
    }
    val FROM_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN linksJson TEXT NOT NULL DEFAULT '[]'")
        }
    }
    val FROM_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN proxyRequired INTEGER NOT NULL DEFAULT 0")
        }
    }
}
