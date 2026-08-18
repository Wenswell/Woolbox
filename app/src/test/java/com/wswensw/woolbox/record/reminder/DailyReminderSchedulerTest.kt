package com.wswensw.woolbox.record.reminder

import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReminderSchedulerTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun activePlanProducesRevisionBoundReminder() {
        assertEquals(
            DailyReminderSpec(today.toString(), 7, 3_600_000),
            dailyReminderSpec(plan(), today),
        )
    }

    @Test
    fun inactiveOrDifferentDayHasNoReminder() {
        assertNull(dailyReminderSpec(plan().copy(state = DailyPlanState.ENOUGH, nextReviewAt = null), today))
        assertNull(dailyReminderSpec(plan().copy(localDate = today.minusDays(1)), today))
    }

    private fun plan() = DailyPlan(
        id = 1,
        localDate = today,
        firstMove = "Go to the library",
        firstMoveCompletedAt = 0,
        primaryTaskId = 1,
        secondaryTaskId = null,
        currentTaskId = 1,
        state = DailyPlanState.ACTIVE,
        completedRounds = 0,
        nextReviewAt = 3_600_000,
        revision = 7,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
    )
}
