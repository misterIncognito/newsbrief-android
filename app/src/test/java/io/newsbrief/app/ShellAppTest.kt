package io.newsbrief.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.newsbrief.app.ui.NewsBriefShell
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = NewsBriefApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellAppTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellShowsWelcomeWithoutFeedControls() {
        compose.setContent { MaterialTheme { NewsBriefShell() } }
        compose.onNodeWithText("NewsBrief").assertIsDisplayed()
        compose.onNodeWithText("Новости в одном месте").assertIsDisplayed()
        compose.onNodeWithText("Обновить").assertDoesNotExist()
        val app = ApplicationProvider.getApplicationContext<NewsBriefApp>()
        assertFalse(app.getDatabasePath("newsbrief.sqlite").exists())
    }

    @Test fun launchingActivityLeavesOfflineFilesAndCredentialsUntouched() {
        val app = ApplicationProvider.getApplicationContext<NewsBriefApp>()
        val database = app.getDatabasePath("newsbrief.sqlite").apply { parentFile!!.mkdirs() }
        val original = "existing-offline-data-must-not-be-opened-or-replaced".toByteArray()
        database.writeBytes(original)
        val image = java.io.File(app.filesDir, "reader-images/saved-image").apply { parentFile!!.mkdirs() }
        image.writeBytes(byteArrayOf(1, 2, 3))
        val prefs = app.getSharedPreferences("newsbrief_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("key_OpenAI", "saved-encrypted-value")
            .putString("telegram_proxy_url", "https://proxy.example").commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertArrayEquals(original, database.readBytes())
            assertArrayEquals(byteArrayOf(1, 2, 3), image.readBytes())
            assertEquals("saved-encrypted-value", prefs.getString("key_OpenAI", null))
            assertEquals("https://proxy.example", prefs.getString("telegram_proxy_url", null))
        } finally { activity.pause().stop().destroy() }
    }
}
