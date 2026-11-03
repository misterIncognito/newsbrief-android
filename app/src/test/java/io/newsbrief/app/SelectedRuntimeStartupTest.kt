package io.newsbrief.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import io.newsbrief.app.data.*
import io.newsbrief.app.network.NewsCollector
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
class SelectedRuntimeStartupTest {
    @Test fun selectedRuntimeAndEveryRegisteredControllerStartWithoutOtherFeatures() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val features = InstalledFeatures.create()
        val background = features.any { it.id == "synchronization" }
        if (background && runCatching { WorkManager.getInstance(app) }.isFailure)
            WorkManager.initialize(app, Configuration.Builder().build())
        val collector = object : NewsCollector {
            override suspend fun collect(source: SourceEntity) = emptyList<ArticleEntity>()
        }
        val runtime = FeatureRuntime(app, features, collector)
        val shell = NewsViewModel(app, runtime.services, features)
        try {
            assertEquals(features.map { it.id }, runtime.features.map { it.id })
            assertEquals("storage" !in features.map { it.id }, runtime.store is MemoryNewsStore)
            runtime.bindings.controllers.keys.forEach { contract ->
                assertNotNull(shell.host.controller(contract))
            }
            assertTrue(shell.overlays.value.isEmpty())
        } finally {
            shell.closeFeatures()
            if (background) WorkManager.getInstance(app).cancelAllWork()
            Dispatchers.resetMain()
        }
    }
}
