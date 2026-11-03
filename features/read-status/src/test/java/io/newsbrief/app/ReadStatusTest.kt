package io.newsbrief.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.ReadStatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReadStatusTest {
    @Test fun seenPostsArePersistedAndCanBeMarkedUnread() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = MemoryNewsStore()
        val source = store.sourceDao().insert(SourceEntity(name = "S", address = "https://example.org", kind = "RSS"))
        val id = store.articleDao().insertAll(listOf(ArticleEntity(sourceId = source, sourceName = "S", title = "A",
            excerpt = "", link = "https://example.org/a", publishedAt = 1))).single()
        val bindings = FeatureBindings.create(emptyList())
        val nav = object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }
        val host = FeatureHost(ApplicationProvider.getApplicationContext<Application>(), FeatureServices(store, MemorySettings(), bindings = bindings), nav) { }
        try {
            val vm = ReadStatusViewModel(host)
            vm.setUnreadOnly(true)
            vm.markVisible(listOf(id))
            assertEquals(setOf(id), vm.visibleReadIds.value)
            assertEquals(listOf(id), store.articleDao().observeUnreadFiltered(emptyList(), false, vm.visibleReadIds.value.toList()).first().map { it.id })
            vm.retainVisible(emptyList())
            assertTrue(vm.visibleReadIds.value.isEmpty())
            val read = store.articleDao().findByLink("https://example.org/a")!!
            assertNotNull(read.readAt)
            assertTrue(store.articleDao().observeUnreadFiltered(emptyList(), false).first().isEmpty())
            vm.setUnreadOnly(false)
            assertTrue(vm.visibleReadIds.value.isEmpty())
            vm.toggleRead(read).join()
            assertNull(store.articleDao().findByLink(read.link)!!.readAt)
            assertEquals(listOf(id), store.articleDao().observeUnreadFiltered(emptyList(), false).first().map { it.id })
            vm.markVisible(listOf(id))
            assertNull("A manual unread choice remains while the article is visible", store.articleDao().findByLink(read.link)!!.readAt)
            vm.retainVisible(emptyList())
            vm.markVisible(listOf(id))
            assertNotNull("The article can be marked read after it leaves and returns", store.articleDao().findByLink(read.link)!!.readAt)
        } finally { host.close(); Dispatchers.resetMain() }
    }
}
