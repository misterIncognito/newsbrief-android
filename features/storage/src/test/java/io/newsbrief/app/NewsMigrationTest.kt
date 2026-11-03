package io.newsbrief.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import io.newsbrief.app.data.NewsDatabase
import io.newsbrief.app.data.NewsMigrations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NewsMigrationTest {
    private fun migrate(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-$version.sqlite"
        context.deleteDatabase(name)
        val legacy = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, kind TEXT NOT NULL, enabled INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_sources_address ON sources(address)")
                    db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, sourceName TEXT NOT NULL, title TEXT NOT NULL, excerpt TEXT NOT NULL, link TEXT NOT NULL, publishedAt INTEGER NOT NULL, importedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_articles_link ON articles(link)")
                    db.execSQL("CREATE INDEX index_articles_publishedAt ON articles(publishedAt)")
                    db.execSQL("CREATE TABLE digests (date TEXT NOT NULL, mode TEXT NOT NULL, content TEXT NOT NULL, referencesJson TEXT NOT NULL, articleCount INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(date,mode))")
                    db.execSQL("INSERT INTO sources VALUES(1,'Saved source','https://example.org/rss','RSS',1)")
                    db.execSQL("INSERT INTO articles VALUES(1,1,'Saved source','Saved title','Saved excerpt','https://example.org/story',123,456)")
                    db.execSQL("INSERT INTO digests VALUES('2026-10-08','SHORT','Saved summary','[]',1,456)")
                    if (version >= 2) {
                        NewsMigrations.FROM_1_2.migrate(db)
                        db.execSQL("UPDATE articles SET content='Offline body', imageUrl='https://example.org/image.jpg'")
                    }
                    if (version >= 3) {
                        NewsMigrations.FROM_2_3.migrate(db)
                        db.execSQL("UPDATE articles SET readerLoaded=1, author='Saved author', imagesJson='[\"https://example.org/photo.jpg\"]'")
                    }
                    db.execSQL("INSERT INTO sources VALUES(2,'Disabled source','https://disabled.example/rss','RSS',0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        legacy.writableDatabase
        legacy.close()
        val migrated = Room.databaseBuilder(context, NewsDatabase::class.java, name)
            .addMigrations(NewsMigrations.FROM_1_2, NewsMigrations.FROM_2_3, NewsMigrations.FROM_3_4, NewsMigrations.FROM_4_5, NewsMigrations.FROM_5_6, NewsMigrations.FROM_6_7, NewsMigrations.FROM_7_8, NewsMigrations.FROM_8_9, NewsMigrations.FROM_9_10).build()
        try {
            val item = migrated.articleDao().findByLink("https://example.org/story")!!
            assertEquals("Saved title", item.title)
            assertEquals(123L, item.publishedAt)
            assertEquals(if (version >= 2) "Offline body" else "", item.content)
            assertEquals(if (version >= 3) "[\"https://example.org/photo.jpg\"]" else "[]", item.imagesJson)
            if (version >= 3) {
                assertTrue(item.readerLoaded)
                assertEquals("Saved author", item.author)
            }
            assertEquals(2, migrated.sourceDao().count())
            assertEquals("Saved source", migrated.sourceDao().enabled().single().name)
            assertEquals("[]", migrated.sourceDao().enabled().single().categoriesJson)
            assertEquals("https://example.org/story", migrated.sourceDao().enabled().single().fetchCursor)
            assertNull(item.readAt)
            migrated.catalogSearchDao().save(io.newsbrief.app.data.CatalogSearchEntity("RSS:tech", "{}", 123))
            assertEquals("{}", migrated.catalogSearchDao().find("RSS:tech")!!.responseJson)
            assertEquals("Saved summary", migrated.digestDao().find("2026-10-08", "SHORT")!!.content)
            migrated.articleDao().updateReader(item.link, "New title", "Full body", "https://example.org/image.jpg", 789, "Author", "[]")
        } finally { migrated.close() }
        val reopened = Room.databaseBuilder(context, NewsDatabase::class.java, name).build()
        try { assertEquals("Full body", reopened.articleDao().findByLink("https://example.org/story")!!.content) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
    @Test fun version1PreservesArticlesSourcesAndSummaries() { migrate(1) }
    @Test fun version2PreservesOfflineContentAndImageMetadata() { migrate(2) }
    @Test fun version3AddsDirectoryCacheWithoutLosingExistingData() { migrate(3) }
}
