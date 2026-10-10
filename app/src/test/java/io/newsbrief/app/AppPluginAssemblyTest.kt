package io.newsbrief.app

import android.app.Application
import io.newsbrief.app.runtime.InstalledFeatures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppPluginAssemblyTest {
    @Test fun assembledApplicationDiscoversExactlyItsSelectedPluginModules() {
        val expected = System.getProperty("newsbrief.expectedFeatures").split(',').filter { it.isNotBlank() }.toSet()
        assertEquals(expected, InstalledFeatures.create().map { it.id }.toSet())
    }
}
