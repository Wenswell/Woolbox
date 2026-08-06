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
    }

    @Test
    fun circularMinuteMathWrapsAcrossMidnight() {
        assertEquals(60, forwardMinutes(23 * 60, 0))
        assertEquals(120, forwardMinutes(23 * 60, 60))
        assertEquals(20, circularMinuteDistance(23 * 60 + 50, 10))
    }

    @Test
    fun goalSatisfactionNeedsAConfiguredGoal() {
        val goal = item().copy(goalValue = 8.0, goalPeriod = GoalPeriod.DAY)

        assertTrue(isGoalSatisfied(goal, 8.0))
        assertEquals(false, isGoalSatisfied(goal, 7.5))
        assertEquals(false, isGoalSatisfied(goal.copy(goalValue = null, goalPeriod = null), 100.0))
    }

    private fun item() = RecordItem(
        id = 1,
        name = "Water",
        description = "",
        mode = RecordMode.ACCUMULATE,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = null,
        goalPeriod = null,
        priority = 0,
        archived = false,
        createdAt = 0,
        updatedAt = 0,
    )
}
