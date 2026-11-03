package io.newsbrief.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderLinksMigrationTest {
    private fun migrate(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reader-links-migration.sqlite"
        context.deleteDatabase(name)
        val legacy = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, kind TEXT NOT NULL, enabled INTEGER NOT NULL, categoriesJson TEXT NOT NULL DEFAULT '[]')")
                    db.execSQL("CREATE UNIQUE INDEX index_sources_address ON sources(address)")
                    db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, sourceName TEXT NOT NULL, title TEXT NOT NULL, excerpt TEXT NOT NULL, content TEXT NOT NULL DEFAULT '', imageUrl TEXT NOT NULL DEFAULT '', readerLoaded INTEGER NOT NULL DEFAULT 0, author TEXT NOT NULL DEFAULT '', imagesJson TEXT NOT NULL DEFAULT '[]', link TEXT NOT NULL, publishedAt INTEGER NOT NULL, importedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_articles_link ON articles(link)")
                    db.execSQL("CREATE INDEX index_articles_publishedAt ON articles(publishedAt)")
                    db.execSQL("CREATE TABLE digests (date TEXT NOT NULL, mode TEXT NOT NULL, content TEXT NOT NULL, referencesJson TEXT NOT NULL, articleCount INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(date,mode))")
                    db.execSQL("CREATE TABLE catalog_searches (queryKey TEXT NOT NULL, responseJson TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(queryKey))")
                    db.execSQL("INSERT INTO sources VALUES(1,'Disabled','https://t.me/s/devby','TELEGRAM',0,'[\"TECH\"]')")
                    db.execSQL("INSERT INTO articles VALUES(1,1,'Disabled','Saved title','Excerpt','Offline full body','https://example.org/photo.jpg',1,'Author','[\"https://example.org/photo.jpg\"]','https://t.me/devby/16806',123,456)")
                    db.execSQL("INSERT INTO digests VALUES('2026-10-08','SHORT','Summary','[]',1,456)")
                    db.execSQL("INSERT INTO catalog_searches VALUES('TELEGRAM:AI','{\"saved\":true}',123)")
                    if (version >= 5) {
                        NewsMigrations.FROM_4_5.migrate(db)
                        db.execSQL("UPDATE articles SET linksJson='[{\"label\":\"Подробнее\",\"url\":\"https://devby.io/news/story\"}]'")
                    }
                    if (version >= 6) {
                        NewsMigrations.FROM_5_6.migrate(db)
                        db.execSQL("UPDATE articles SET proxyRequired=1")
                    }
                    if (version >= 7) {
                        NewsMigrations.FROM_6_7.migrate(db)
                        db.execSQL("INSERT INTO saved_articles VALUES('https://t.me/devby/16806',789)")
                    }
                    if (version >= 8) NewsMigrations.FROM_7_8.migrate(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        legacy.writableDatabase; legacy.close()
        val db = Room.databaseBuilder(context, NewsDatabase::class.java, name)
            .addMigrations(NewsMigrations.FROM_4_5, NewsMigrations.FROM_5_6, NewsMigrations.FROM_6_7, NewsMigrations.FROM_7_8, NewsMigrations.FROM_8_9).build()
        try {
            val article = db.articleDao().findByLink("https://t.me/devby/16806")!!
            assertEquals("Offline full body", article.content)
            assertEquals(if (version == 4) "[]" else "[{\"label\":\"Подробнее\",\"url\":\"https://devby.io/news/story\"}]", article.linksJson)
            assertEquals(version >= 6, article.proxyRequired)
            assertEquals(SourceKind.TELEGRAM, article.sourcePluginId)
            assertEquals("Author", article.author)
            assertTrue(article.readerLoaded)
            assertEquals(123L, article.publishedAt)
            assertEquals(456L, article.importedAt)
            assertNull(article.readAt)
            assertEquals("", db.sourceDao().observe().first().single().fetchCursor)
            db.articleDao().setReadAt(article.link, 999L)
            db.sourceDao().updateFetchCursor(1, "16806")
            assertEquals(999L, db.articleDao().findByLink(article.link)!!.readAt)
            assertEquals("16806", db.sourceDao().observe().first().single().fetchCursor)
            assertEquals(1, db.sourceDao().count())
            assertTrue(db.sourceDao().enabled().isEmpty())
            assertEquals("Summary", db.digestDao().find("2026-10-08", "SHORT")!!.content)
            assertEquals("{\"saved\":true}", db.catalogSearchDao().find("TELEGRAM:AI")!!.responseJson)
            val links = "[{\"label\":\"Подробнее\",\"url\":\"https://devby.io/news/story\"}]"
            db.articleDao().fillMissingPreview(article.link, "New preview", "", links)
            db.articleDao().requireProxy(article.link)
            assertEquals(links, db.articleDao().findByLink(article.link)!!.linksJson)
            assertEquals(if (version >= 7) listOf(article.link) else emptyList<String>(), db.savedArticleDao().observeUrls().first())
            db.savedArticleDao().insert(SavedArticleEntity(article.link, 789))
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, NewsDatabase::class.java, name).build()
        try {
            val saved = reopened.articleDao().findByLink("https://t.me/devby/16806")!!
            assertEquals(SourceKind.TELEGRAM, saved.sourcePluginId)
            assertEquals("Offline full body", saved.content)
            assertTrue(saved.proxyRequired)
            assertTrue(saved.linksJson.contains("devby.io/news/story"))
            assertEquals(saved, reopened.articleDao().observeSaved().first().single())
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
    @Test fun version7PreservesBookmarksAndBackfillsPluginOrigin() { migrate(7) }
    @Test fun version4PreservesOfflineArticlesDisabledSourcesSummariesAndCatalog() { migrate(4) }
    @Test fun version5PreservesSavedLinksAndPersistsProxyChoiceAfterReopen() { migrate(5) }
    @Test fun version6AddsBookmarksWithoutLosingArticlesSettingsMetadataOrProxyChoice() { migrate(6) }
    @Test fun version8PreservesDataAndAddsReadStateAndCursor() { migrate(8) }

}
