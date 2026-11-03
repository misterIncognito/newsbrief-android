package io.newsbrief.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.newsbrief.app.ui.FeedAutoRefresh
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FeedAutoRefreshTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    @Test fun refreshesOnTabReturnReaderReturnAndForegroundButNeverWhileFeedHidden() {
        val owner = Owner()
        var visible by mutableStateOf(false)
        var unrelated by mutableIntStateOf(0)
        var requests = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val ignored = unrelated
                FeedAutoRefresh(visible) { requests++; ignored.hashCode() }
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(0, requests); visible = true }
        compose.runOnIdle { assertEquals(1, requests); unrelated++ }
        compose.runOnIdle { assertEquals(1, requests); visible = false }
        compose.runOnIdle { visible = true }
        compose.runOnIdle { assertEquals(2, requests); owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(3, requests); visible = false }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(3, requests) }
    }
}
