package io.newsbrief.app

import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.runtime.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Startup plugins must be able to access their Application's completed runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = StartupProbeApp::class)
class StartupHookOrderTest {
    @Test fun hooksSeePublishedServicesAndRunOnlyOnce() {
        val app = ApplicationProvider.getApplicationContext<StartupProbeApp>()
        assertTrue(app.servicesReady)
        assertEquals(1, app.startCalls)
        app.runtime.start()
        assertEquals(1, app.startCalls)
    }
}
class StartupProbeApp : NewsBriefApp() {
    var startCalls = 0
    var servicesReady = false
    override fun installedFeatures() = listOf(object : AppFeature {
        override val id = "startup-probe"
        override fun configure(bindings: FeatureBindings) {
            bindings.start = { context ->
                val app = context as StartupProbeApp
                app.startCalls++
                app.servicesReady = app.runtime.services.settings === app.runtime.settings
            }
        }
    })
}
