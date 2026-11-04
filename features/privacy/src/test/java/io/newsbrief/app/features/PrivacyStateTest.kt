package io.newsbrief.app.features

import org.junit.Assert.*
import org.junit.Test

class PrivacyStateTest {
    @Test fun launchAndBackgroundSettingsAreIndependent() {
        assertFalse(PrivacyState(true, true, false).unlocked)
        val background = PrivacyState(true, false, true)
        assertTrue(background.unlocked)
        assertTrue(background.shouldLockOnStop())
        background.authenticating = true
        assertFalse(background.shouldLockOnStop())
        assertFalse(PrivacyState(true, false, true, pendingLock = true).unlocked)
        assertTrue(PrivacyState(false, true, true, pendingLock = true).unlocked)
        assertFalse(PrivacyState(true, true, false).shouldLockOnStop())
    }
}
