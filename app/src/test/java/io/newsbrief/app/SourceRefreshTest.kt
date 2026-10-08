package io.newsbrief.app

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
import io.newsbrief.app.ui.NewsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SourceRefreshTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun settings(): SettingsStore = SettingsStore(app).apply {
        markSourcesCatalogV2Seeded()
        markSourcesCatalogV3Seeded()
        markSourcesCatalogV4Seeded()
    }
    private fun article(source: SourceEntity, content: String = "") = ArticleEntity(
        sourceId = source.id, sourceName = source.name, title = source.name, excerpt = "Preview",
        content = content, link = source.address.replace("/rss", "/story"), publishedAt = 123)

    @Test fun disabledAndDeletedSourcesDisappearButOfflineArticlesRemain() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val first = SourceEntity(name = "First", address = "https://first.example/rss", kind = SourceKind.RSS)
            val second = SourceEntity(name = "Second", address = "https://second.example/rss", kind = SourceKind.RSS)
            val firstId = db.sourceDao().insert(first)
            val secondId = db.sourceDao().insert(second)
            val saved = article(first.copy(id = firstId), "Offline full text")
            db.articleDao().insertAll(listOf(saved, article(second.copy(id = secondId))))
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
            }, settings())
            assertEquals(2, repo.observeLatest().first().size)
            repo.toggleSource(firstId, false)
            assertEquals(listOf("Second"), repo.observeLatest().first().map { it.sourceName })
            assertEquals(listOf("Second"), db.articleDao().recent(0, 600).map { it.sourceName })
            assertEquals("Offline full text", repo.lookup(saved.link)!!.content)
            repo.toggleSource(firstId, true)
            assertEquals(2, repo.observeLatest().first().size)
            repo.removeSource(firstId)
            assertEquals(listOf("Second"), repo.observeLatest().first().map { it.sourceName })
            assertEquals("Offline full text", repo.lookup(saved.link)!!.content)
        } finally { db.close() }
    }

    @Test fun addingSourceDuringRefreshQueuesAnotherPass() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var vm: NewsViewModel? = null
        val release = CompletableDeferred<Unit>()
        try {
            db.sourceDao().insert(SourceEntity(name = "First", address = "https://first.example/rss", kind = SourceKind.RSS))
            val started = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val collector = object : NewsCollector {
                override suspend fun collect(source: SourceEntity): List<ArticleEntity> {
                    synchronized(calls) { calls += source.name }
                    if (!started.isCompleted) { started.complete(Unit); release.await() }
                    return listOf(article(source))
                }
            }
            val store = settings()
            val repo = NewsRepository(db, collector, store)
            val model = NewsViewModel(app, repo, store)
            vm = model
            withTimeout(5000) { started.await() }
            model.addSource("Second", SourceKind.RSS, "https://second.example/rss").join()
            assertTrue(model.refreshing.value)
            release.complete(Unit)
            withTimeout(5000) { model.refreshing.first { !it } }
            assertEquals(setOf("First", "Second"), repo.observeLatest().first().map { it.sourceName }.toSet())
            assertEquals(2, calls.count { it == "First" })
            assertEquals(1, calls.count { it == "Second" })
        } finally {
            release.complete(Unit)
            vm?.viewModelScope?.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun readdingDeletedSourceRestoresCachedArticlesWithoutReplacingText() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        try {
            val source = SourceEntity(name = "Saved", address = "https://saved.example/rss", kind = SourceKind.RSS)
            val oldId = db.sourceDao().insert(source)
            val cached = article(source.copy(id = oldId), "Saved reader text")
            db.articleDao().insertAll(listOf(cached))
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = listOf(article(source))
            }, settings())
            repo.removeSource(oldId)
            assertTrue(repo.observeLatest().first().isEmpty())
            repo.addSource(source.name, source.kind, source.address)
            repo.refresh()
            assertEquals("Saved reader text", repo.observeLatest().first().single().content)
            assertNotEquals(oldId, repo.observeLatest().first().single().sourceId)
        } finally { db.close() }
    }
    @Test fun enablingSourceFetchesItsArticlesAutomatically() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(app, NewsDatabase::class.java).build()
        var vm: NewsViewModel? = null
        try {
            val id = db.sourceDao().insert(SourceEntity(name = "Enabled later", address = "https://later.example/rss",
                kind = SourceKind.RSS, enabled = false))
            val store = settings()
            val repo = NewsRepository(db, object : NewsCollector {
                override suspend fun collect(source: SourceEntity) = listOf(article(source))
            }, store)
            val model = NewsViewModel(app, repo, store)
            vm = model
            model.toggleSource(id, true).join()
            withTimeout(5000) { model.refreshing.first { !it } }
            assertEquals("Enabled later", repo.observeLatest().first().single().sourceName)
        } finally {
            vm?.viewModelScope?.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
