package io.newsbrief.app

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.data.*
import io.newsbrief.app.features.SourceSetupFeature
import io.newsbrief.app.runtime.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceSetupFeatureTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clearSetup() { app.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE).edit().clear().commit() }
    private fun host(store: MemoryNewsStore, settings: MemorySettings = MemorySettings()): FeatureHost {
        val nav = object : ShellNavigator {
            override fun show(id: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) = Unit
            override fun dismiss(id: String) = Unit
        }
        return FeatureHost(app, FeatureServices(store, settings, bindings = FeatureBindings.create(emptyList())), nav) { }
    }
    @Test fun freshInstallShowsAvailablePagesAndFinishesOnce() {
        val store = MemoryNewsStore(); val host = host(store); val finished = AtomicInteger()
        try {
            compose.setContent { MaterialTheme {
                if (!SourceSetupFeature().ShowSetup(host, { it in setOf("source-discovery", "source-management") },
                        { Text("Page: $it") }, { finished.incrementAndGet() })) Text("Feed ready")
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Welcome to NewsBrief").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Page: source-discovery").assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Page: source-management").assertIsDisplayed()
            runBlocking { store.sourceDao().insert(SourceEntity(name = "Example", address = "https://example.org/feed", kind = "RSS")) }
            compose.onNodeWithText("Finish and update feed").performClick()
            compose.onNodeWithText("Feed ready").assertIsDisplayed()
            assertEquals(1, finished.get())
            assertTrue(app.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE).getBoolean("completed", false))
            assertEquals(1, runBlocking { store.sourceDao().count() })
        } finally { host.close() }
    }
    @Test fun catalogAndCustomPagesAreOptional() {
        val host = host(MemoryNewsStore()); val finished = AtomicInteger()
        try {
            compose.setContent { MaterialTheme {
                if (!SourceSetupFeature().ShowSetup(host, { false }, { Text("Unexpected page") }, { finished.incrementAndGet() })) Text("Feed ready")
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Welcome to NewsBrief").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Finish and update feed").performClick()
            compose.onNodeWithText("Feed ready").assertExists()
            assertEquals(1, finished.get())
        } finally { host.close() }
    }
    @Test fun existingSourcesAndPreviousInstallationsSkipSetup() {
        val store = MemoryNewsStore()
        runBlocking { store.sourceDao().insert(SourceEntity(name = "Existing", address = "https://example.org/feed", kind = "RSS")) }
        val host = host(store); val finished = AtomicInteger()
        try {
            compose.setContent { MaterialTheme {
                if (!SourceSetupFeature().ShowSetup(host, { true }, { Text("Unexpected page") }, { finished.incrementAndGet() })) Text("Feed ready")
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Feed ready").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, finished.get())
            assertEquals(1, runBlocking { store.sourceDao().count() })
        } finally { host.close() }
    }
    @Test fun previousInstallationWithNoSourcesSkipsSetup() {
        val settings = MemorySettings().apply { markSourcesCatalogV5Seeded() }
        val host = host(MemoryNewsStore(), settings)
        try {
            compose.setContent { MaterialTheme {
                if (!SourceSetupFeature().ShowSetup(host, { false }, { }, { })) Text("Feed ready")
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Feed ready").fetchSemanticsNodes().isNotEmpty() }
        } finally { host.close() }
    }
    @Test fun unfinishedSetupResumesEvenAfterSourceWasAdded() {
        app.getSharedPreferences("newsbrief_source_setup", Context.MODE_PRIVATE).edit().putBoolean("started", true).commit()
        val store = MemoryNewsStore()
        runBlocking { store.sourceDao().insert(SourceEntity(name = "Chosen", address = "https://example.org/feed", kind = "RSS")) }
        val host = host(store)
        try {
            compose.setContent { MaterialTheme {
                if (!SourceSetupFeature().ShowSetup(host, { false }, { }, { })) Text("Feed ready")
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Welcome to NewsBrief").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Finish and update feed").assertExists()
        } finally { host.close() }
    }
}
