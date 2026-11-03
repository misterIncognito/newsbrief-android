package io.newsbrief.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisibilityReadTrackerTest {
    @Test fun articlesRequireTwoContinuousSecondsAndAreTimedIndependently() = runTest {
        val read = mutableListOf<Long>()
        val tracker = VisibilityReadTracker(this, {}) { read.addAll(it) }
        tracker.update(setOf(1), setOf(1))
        runCurrent()
        advanceTimeBy(1_000)
        tracker.update(setOf(1, 2), setOf(1, 2))
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(emptyList<Long>(), read)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1L), read)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1L, 2L), read)
        tracker.close()
    }

    @Test fun leavingEligibilityCancelsTheTimerAndReturningStartsAgain() = runTest {
        val read = mutableListOf<Long>()
        val tracker = VisibilityReadTracker(this, {}) { read.addAll(it) }
        tracker.update(setOf(1), setOf(1))
        runCurrent()
        advanceTimeBy(1_999)
        tracker.update(setOf(1), emptySet())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<Long>(), read)
        tracker.update(setOf(1), setOf(1))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(1L), read)
        tracker.update(setOf(1), setOf(1))
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(1L), read)
        tracker.update(emptySet(), emptySet())
        tracker.update(setOf(1), setOf(1))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(1L, 1L), read)
        tracker.close()
    }

    @Test fun closingCancelsPendingMarks() = runTest {
        val read = mutableListOf<Long>()
        val tracker = VisibilityReadTracker(this, {}) { read.addAll(it) }
        tracker.update(setOf(1), setOf(1))
        runCurrent()
        tracker.close()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<Long>(), read)
    }
}
