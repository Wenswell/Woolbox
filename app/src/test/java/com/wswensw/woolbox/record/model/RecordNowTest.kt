package com.wswensw.woolbox.record.model

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordNowTest {
    @Test
    fun clockKeepsLocalDateAndMinuteOfDay() {
        val clock = RecordsClock.from(LocalDateTime.of(2026, 7, 26, 14, 32))
        assertEquals(14 * 60 + 32, clock.minuteOfDay)
        assertEquals(2026, clock.date.year)
        assertEquals(0.605f, clock.dayFraction, 0.001f)
        assertEquals(0f, RecordsClock.from(LocalDateTime.of(2026, 7, 26, 0, 0)).dayFraction, 0f)
    }

    @Test
    fun forwardMinutesWrapsAcrossMidnight() {
        assertEquals(0, forwardMinutes(600, 600))
        assertEquals(30, forwardMinutes(600, 630))
        assertEquals(60, forwardMinutes(23 * 60, 24 * 60 % MINUTES_PER_DAY))
        assertEquals(120, forwardMinutes(23 * 60, 60))
    }

    @Test
    fun circularDistanceTakesTheShorterDirection() {
        assertEquals(0, circularMinuteDistance(600, 600))
        assertEquals(30, circularMinuteDistance(600, 630))
        assertEquals(30, circularMinuteDistance(630, 600))
        // Across midnight the short way is 20 minutes, not 1420.
        assertEquals(20, circularMinuteDistance(23 * 60 + 50, 10))
    }

    @Test
    fun windowLengthMeasuresTheOpenSpan() {
        assertEquals(120, windowLengthMinutes(window(8 * 60, 10 * 60)))
        assertEquals(360, windowLengthMinutes(window(23 * 60, 5 * 60)))
    }

    @Test
    fun goalSatisfactionNeedsAConfiguredGoal() {
        val goal = accumulate(id = 1, name = "Water", windows = emptyList())
            .copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)
        assertTrue(isGoalSatisfied(goal, 8.0))
        assertEquals(false, isGoalSatisfied(goal, 7.5))
        assertEquals(false, isGoalSatisfied(goal.copy(goalValue = null, goalPeriod = null), 100.0))
    }

    @Test
    fun windowRecordedMatchesEntriesInsideTheWindowOnly() {
        val window = window(8 * 60, 10 * 60)
        assertTrue(isWindowRecorded(window, listOf(9 * 60)))
        assertEquals(false, isWindowRecorded(window, listOf(10 * 60)))
        assertEquals(false, isWindowRecorded(window, null))
        // Overnight windows only see minutes on today's local date.
        assertTrue(isWindowRecorded(window(23 * 60, 2 * 60), listOf(30)))
    }

    @Test
    fun windowCoverageCountsOverlapsOnceAndWrapsMidnight() {
        assertEquals(0, windowCoverageMinutes(emptyList()))
        assertEquals(120, windowCoverageMinutes(listOf(window(8 * 60, 10 * 60))))
        assertEquals(
            480,
            windowCoverageMinutes(listOf(window(8 * 60, 15 * 60), window(10 * 60, 16 * 60))),
        )
        assertEquals(360, windowCoverageMinutes(listOf(window(23 * 60, 5 * 60))))
    }

    @Test
    fun validatedWindowsRejectRepeatsEqualBoundsAndWideCoverage() {
        assertEquals(
            listOf(window(8 * 60, 10 * 60)),
            validatedWindows(listOf(window(8 * 60, 10 * 60))),
        )
        assertThrows { validatedWindows(listOf(window(8 * 60, 8 * 60))) }
        assertThrows {
            validatedWindows(listOf(window(8 * 60, 10 * 60), window(8 * 60, 10 * 60)))
        }
        assertThrows { validatedWindows(listOf(window(0, MAX_WINDOW_COVERAGE_MINUTES))) }
        // Exactly at the cap is rejected; one minute under is accepted.
        assertEquals(
            1,
            validatedWindows(listOf(window(0, MAX_WINDOW_COVERAGE_MINUTES - 1))).size,
        )
    }

    private fun assertThrows(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is RecordValidationException)
    }

    private fun window(startMinute: Int, endMinute: Int) =
        RecordWindow(startMinute = startMinute, endMinute = endMinute)

    private fun accumulate(
        id: Long,
        name: String,
        windows: List<Pair<Int, Int>>,
        priority: Int = 0,
    ) = RecordItem(
        id = id,
        name = name,
        description = "",
        mode = RecordMode.ACCUMULATE,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = null,
        goalPeriod = null,
        priority = priority,
        archived = false,
        windows = windows.map { (start, end) -> RecordWindow(startMinute = start, endMinute = end) },
        createdAt = 0,
        updatedAt = 0,
    )
}
