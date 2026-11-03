package io.newsbrief.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.runtime.*
import io.newsbrief.app.ui.NewsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureHostLifecycleTest {
    interface Port { val job: Job }
    class Controller : ViewModel(), Port { override val job = viewModelScope.launch { awaitCancellation() } }
    @Test fun controllersAreLazyScopedOnceAndCancelledWhenShellClears() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            var created = 0
            val bindings = FeatureBindings().apply { controller(Port::class.java) { created++; Controller() } }
            val services = FeatureServices(MemoryNewsStore(), MemorySettings(), bindings = bindings)
            val shell = NewsViewModel(ApplicationProvider.getApplicationContext(), services, emptyList())
            assertEquals(0, created)
            assertNull(shell.host.optional(Runnable::class.java))
            val first = shell.host.controller(Port::class.java)
            assertSame(first, shell.host.controller(Port::class.java))
            assertEquals(1, created); assertTrue(first.job.isActive)
            shell.closeFeatures()
            assertTrue(first.job.isCancelled)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun duplicateControllerRegistrationsFailRatherThanReplaceAnotherPlugin() {
        val bindings = FeatureBindings().apply { controller(Port::class.java) { Controller() } }
        try { bindings.controller(Port::class.java) { Controller() }; fail("Duplicate controller accepted") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Duplicate")) }
    }
    @Test fun emptyShellDoesNotCreateFeatureServicesOrOverlays() = runBlocking {
        val store = MemoryNewsStore()
        val services = FeatureServices(store, MemorySettings(), bindings = FeatureBindings.create(emptyList()))
        val shell = NewsViewModel(ApplicationProvider.getApplicationContext(), services, emptyList())
        assertTrue(shell.host.bindings.controllers.isEmpty())
        assertNull(services.articles); assertNull(services.sourceActions); assertNull(services.directory)
        assertEquals(0, services.refresh().added)
        assertEquals(0, store.sourceDao().count())
        assertTrue(shell.overlays.value.isEmpty()); shell.closeFeatures()
    }
}
